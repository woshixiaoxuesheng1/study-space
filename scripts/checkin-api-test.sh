#!/bin/bash
# 签到签退模块接口冒烟测试（F-12 / F-13 / F-14，A-18 / A-19 / A-25）
# 用法：bash scripts/checkin-api-test.sh
# 前提：服务跑在 8080 端口，数据库已执行过 sql/init.sql（脚本会写入真实预约与签到记录）
# 说明：T9 需要等到预约开始时间之后才能验证"迟到签到"，默认会多等 70 秒；
#       不想等可以 WAIT_LATE=0 bash scripts/checkin-api-test.sh
BASE=http://127.0.0.1:8080
TMP=/tmp/ct; mkdir -p $TMP
WAIT_LATE=${WAIT_LATE:-1}

login() {
  curl -s -X POST $BASE/api/login -H "Content-Type: application/json" \
    -d "{\"studentNo\":\"$1\",\"password\":\"123456\"}" \
    | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])"
}
req() { # method path token [data]
  if [ -n "$4" ]; then
    curl -s -X "$1" "$BASE$2" -H "Authorization: Bearer $3" -H "Content-Type: application/json" -d "$4"
  else
    curl -s -X "$1" "$BASE$2" -H "Authorization: Bearer $3" -H "Content-Type: application/json"
  fi
}
show() {
  python -c "import sys,json;d=json.load(sys.stdin);print('  code=%s | %s'%(d['code'],d['message']))"
}
g() { # 取 data 下的字段
  python -c "import sys,json;d=json.load(sys.stdin);v=(d.get('data') or {}).get('$1');print('' if v is None else v)"
}
tomorrow() { date -d '+1 day' '+%Y-%m-%d'; }
plus() { date -d "+$1 minutes" '+%Y-%m-%d %H:%M:00'; }

ZS=$(login 20230101001); LS=$(login 20230101002); AD=$(login A0001)
D=$(tomorrow)
echo "== 当前：$(date '+%Y-%m-%d %H:%M:%S') =="

# ------------------------------------------------------------------
echo "T1 创建马上开始的预约（开始前 2 分钟，签到窗口已开）"
S=$(plus 2); E=$(plus 62)
# 3 号自习室 07:30-23:00，比 1 号房（22:00 关）留的余量更大，晚上跑脚本也不容易越界
if [ "$(date -d "$E" '+%s')" -gt "$(date -d "$(date '+%Y-%m-%d') 22:55" '+%s')" ]; then
  echo "  当前时间太晚，预约结束时间会超过自习室关闭时间，请改天再跑（建议 21:30 前）"
  exit 1
fi
R=$(req POST /api/bookings "$ZS" "{\"seatId\":201,\"startTime\":\"$S\",\"endTime\":\"$E\"}")
echo "$R" | show
ID=$(echo "$R" | g id); CODE=$(echo "$R" | g checkInCode)
echo "  bookingId=$ID 签到码=$CODE 窗口=$(echo "$R" | g checkInWindowStart) ~ $(echo "$R" | g checkInDeadline)"

echo "T2 查签到凭证（应 canCheckIn=true、下发签到码、有倒计时）"
req GET "/api/bookings/$ID/check-in-code" "$ZS" \
  | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('  canCheckIn=%s code=%s remainSeconds=%s %s %s'%(d['canCheckIn'],d['checkInCode'],d['remainSeconds'],d['roomName'],d['seatNo']))"

echo "T3 李四查张三的签到凭证（应 403）"
req GET "/api/bookings/$ID/check-in-code" "$LS" | show

echo "T4 管理员查签到凭证（应成功，用于服务台核对）"
req GET "/api/bookings/$ID/check-in-code" "$AD" | show

echo "T5 错误签到码（应 3010）"
req POST "/api/bookings/$ID/check-in" "$ZS" '{"code":"0000"}' | show

echo "T6 还没签到就点签退（应 3008 还没有签到）"
req POST "/api/bookings/$ID/check-out" "$ZS" | show

echo "T7 李四替张三签到（应 403，签到只认本人令牌）"
req POST "/api/bookings/$ID/check-in" "$LS" "{\"code\":\"$CODE\"}" | show

echo "T8 预约明天 10:00 的座位，提前一天签到（应 3009 窗口未开）"
B2=$(req POST /api/bookings "$ZS" "{\"seatId\":6,\"startTime\":\"$D 10:00:00\",\"endTime\":\"$D 12:00:00\"}")
echo "$B2" | show
ID2=$(echo "$B2" | g id); CODE2=$(echo "$B2" | g checkInCode)
req POST "/api/bookings/$ID2/check-in" "$ZS" "{\"code\":\"$CODE2\"}" | show
echo "  再查凭证（应 canCheckIn=false 且不下发签到码）"
req GET "/api/bookings/$ID2/check-in-code" "$ZS" \
  | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('  canCheckIn=%s code=%s windowStart=%s'%(d['canCheckIn'],d['checkInCode'],d['windowStart']))"

if [ "$WAIT_LATE" = "1" ]; then
  echo "T9 等到开始时间之后 1 分钟再签到，验证迟到标记"
  # 目标时间 = 开始时间 + 65 秒，保证签到时间至少晚 1 分钟（lateMinutes >= 1）
  TARGET=$(( $(date -d "$S" '+%s') + 65 ))
  for i in $(seq 1 45); do
    [ "$(date '+%s')" -ge "$TARGET" ] && break
    sleep 5
  done
  echo "  当前 $(date '+%H:%M:%S')，预约开始 $S"
fi
echo "T10 签到（窗口内正确码，应成功；若已过开始时间会带迟到分钟）"
R3=$(req POST "/api/bookings/$ID/check-in" "$ZS" "{\"code\":\"$CODE\"}")
echo "$R3" | show
echo "  status=$(echo "$R3" | g status) 签到时间=$(echo "$R3" | g checkInTime) 迟到=$(echo "$R3" | g lateMinutes) 分钟"
echo "  签到后预约里的码应已清空：$(echo "$R3" | g checkInCode)"

echo "T11 重复签到（应 3008 已签到）"
req POST "/api/bookings/$ID/check-in" "$ZS" "{\"code\":\"$CODE\"}" | show

echo "T12 使用中详情（应 canCheckOut=true、usedMinutes 有值）"
req GET "/api/bookings/$ID" "$ZS" \
  | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('  status=%s canCheckIn=%s canCheckOut=%s lateCheckIn=%s usedMinutes=%s'%(d['status'],d['canCheckIn'],d['canCheckOut'],d['lateCheckIn'],d['usedMinutes']))"

echo "T13 提前签退（应成功，earlyCheckOut=true，座位释放）"
req POST "/api/bookings/$ID/check-out" "$ZS" | show
req GET "/api/bookings/$ID" "$ZS" \
  | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('  status=%s 签退时间=%s 提前签退=%s 实际使用=%s 分钟 canCheckOut=%s'%(d['status'],d['checkOutTime'],d['earlyCheckOut'],d['usedMinutes'],d['canCheckOut']))"

echo "T14 重复签退（应 3008 已结束）"
req POST "/api/bookings/$ID/check-out" "$ZS" | show

echo "T15 签退后座位回到空闲列表（查未来 20 分钟，A01 应重新出现）"
TODAY=$(date '+%Y-%m-%d')
FS_START=$(date -d '+5 minutes' '+%H:%M'); FS_END=$(date -d '+25 minutes' '+%H:%M')
FS=$(curl -s "$BASE/api/rooms/3/free-seats?date=$TODAY&startTime=$FS_START&endTime=$FS_END" -H "Authorization: Bearer $ZS")
echo "$FS" | python -c "
import sys,json
d=json.load(sys.stdin)
if d.get('data') is None:
    print('  跳过：%s（当前时间太晚，超出自习室开放时间）' % d.get('message'))
else:
    print('  %s~%s 空闲座位数=%s 含 A01=%s' % ('$FS_START','$FS_END', len(d['data']), any(s['seatNo']=='A01' for s in d['data'])))
"

echo "T16 不带令牌查签到凭证（应 HTTP 401）"
curl -s -o /dev/null -w "  http=%{http_code}\n" "$BASE/api/bookings/$ID/check-in-code"

echo "T17 管理员查看学生签到记录（应能看到签到/签退时间）"
req GET "/api/bookings/$ID" "$AD" \
  | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('  学生=%s 座位=%s %s 签到=%s 签退=%s 状态=%s'%(d['userName'],d['roomName'],d['seatNo'],d['checkInTime'],d['checkOutTime'],d['statusText']))"

echo "== 测试结束，清理：取消 T8 那条明天的预约 =="
req DELETE "/api/bookings/$ID2?reason=test-cleanup" "$ZS" | show
