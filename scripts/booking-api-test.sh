#!/bin/bash
# 预约模块接口冒烟测试（A-14 ~ A-19）
# 用法：bash scripts/booking-api-test.sh
# 前提：服务跑在 8080 端口，数据库已执行过 sql/init.sql（脚本会写入真实预约数据，建议先重置库再跑）
BASE=http://127.0.0.1:8080
TMP=/tmp/bt; mkdir -p $TMP

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
field() { # fieldname
  python -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('$1',''))"
}
tomorrow() { date -d '+1 day' '+%Y-%m-%d'; }
plus() { date -d "+$1 minutes" '+%Y-%m-%d %H:%M:00'; }

ZS=$(login 20230101001); LS=$(login 20230101002); WW=$(login 20230101003); AD=$(login A0001)
D=$(tomorrow)
echo "== 测试日期：$D 当前：$(date '+%Y-%m-%d %H:%M:%S') =="

echo "T1 正常预约（张三，5 号座 $D 10:00-12:00）"
B=$(req POST /api/bookings "$ZS" "{\"seatId\":5,\"startTime\":\"$D 10:00:00\",\"endTime\":\"$D 12:00:00\"}")
echo "$B" | show
ID1=$(echo "$B" | field id); echo "  bookingNo=$(echo "$B" | field bookingNo) 签到码=$(echo "$B" | field checkInCode) canCancel=$(echo "$B" | field canCancel)"

echo "T2 同时段同座位（应 3004 + 推荐空位）"
req POST /api/bookings "$ZS" "{\"seatId\":5,\"startTime\":\"$D 11:00:00\",\"endTime\":\"$D 13:00:00\"}" | show

echo "T3 同人同时段换座位（应 3005）"
req POST /api/bookings "$ZS" "{\"seatId\":6,\"startTime\":\"$D 11:00:00\",\"endTime\":\"$D 12:00:00\"}" | show

echo "T4 时间接续 12:00 开始（V-07，应成功）"
req POST /api/bookings "$ZS" "{\"seatId\":5,\"startTime\":\"$D 12:00:00\",\"endTime\":\"$D 14:00:00\"}" | show

echo "T5 李四查张三的预约（应 403）"
req GET "/api/bookings/$ID1" "$LS" | show

echo "T6 不带令牌访问列表（应 HTTP 401）"
curl -s -o /dev/null -w "  http=%{http_code}\n" $BASE/api/bookings

echo "T7 预约过去时间（应 3012）"
PAST_S=$(date -d '-2 hours' '+%Y-%m-%d %H:%M:00'); PAST_E=$(date -d '-1 hour' '+%Y-%m-%d %H:%M:00')
req POST /api/bookings "$ZS" "{\"seatId\":7,\"startTime\":\"$PAST_S\",\"endTime\":\"$PAST_E\"}" | show

echo "T8 跨天预约（应 400）"
DAY2=$(date -d '+2 day' '+%Y-%m-%d')
req POST /api/bookings "$ZS" "{\"seatId\":7,\"startTime\":\"$D 22:00:00\",\"endTime\":\"$DAY2 01:00:00\"}" | show

echo "T9 王五信用分 50 预约（应 3007）"
req POST /api/bookings "$WW" "{\"seatId\":7,\"startTime\":\"$D 09:00:00\",\"endTime\":\"$D 10:00:00\"}" | show

echo "T10 张三第 3 次预约（应成功）"
req POST /api/bookings "$ZS" "{\"seatId\":9,\"startTime\":\"$D 20:00:00\",\"endTime\":\"$D 21:00:00\"}" | show

echo "T11 张三第 4 次预约（应 3006 每日上限）"
req POST /api/bookings "$ZS" "{\"seatId\":10,\"startTime\":\"$D 21:00:00\",\"endTime\":\"$D 22:00:00\"}" | show

echo "T12 提前取消明天的预约（应免费）"
req DELETE "/api/bookings/$ID1?reason=test-free-cancel" "$ZS" | show

echo "T13 开始前 20 分钟取消（应扣 10 分）"
S=$(plus 20); E=$(plus 80)
B2=$(req POST /api/bookings "$ZS" "{\"seatId\":7,\"startTime\":\"$S\",\"endTime\":\"$E\"}")
echo "$B2" | show
ID2=$(echo "$B2" | field id)
req DELETE "/api/bookings/$ID2?reason=late" "$ZS" | show

echo "T14 签到窗口内签到（开始前 10 分钟）"
S3=$(plus 10); E3=$(plus 70)
B3=$(req POST /api/bookings "$ZS" "{\"seatId\":8,\"startTime\":\"$S3\",\"endTime\":\"$E3\"}")
echo "$B3" | show
ID3=$(echo "$B3" | field id); CODE=$(echo "$B3" | field checkInCode)
echo "  bookingId=$ID3 签到码=$CODE canCheckIn=$(echo "$B3" | field canCheckIn)"
echo "  错误签到码 ->"
req POST "/api/bookings/$ID3/check-in" "$ZS" '{"code":"9999"}' | show
echo "  正确签到码 ->"
req POST "/api/bookings/$ID3/check-in" "$ZS" "{\"code\":\"$CODE\"}" | show
echo "  签退 ->"
req POST "/api/bookings/$ID3/check-out" "$ZS" | show

echo "T15 并发抢座（李四 / 管理员同时订 10 号座 $D 09:00-10:00）"
req POST /api/bookings "$LS" "{\"seatId\":10,\"startTime\":\"$D 09:00:00\",\"endTime\":\"$D 10:00:00\"}" > $TMP/c1.json &
req POST /api/bookings "$AD" "{\"seatId\":10,\"startTime\":\"$D 09:00:00\",\"endTime\":\"$D 10:00:00\"}" > $TMP/c2.json &
wait
echo "  请求1:"; cat $TMP/c1.json | show
echo "  请求2:"; cat $TMP/c2.json | show

echo "T16 我的预约列表（按状态筛选）"
curl -s "$BASE/api/bookings?page=1&size=5" -H "Authorization: Bearer $ZS" \
  | python -c "import sys,json;d=json.load(sys.stdin)['data'];print('  total=%s'%d['total']);[print('  #%s %s %s %s-%s %s(%s)'%(b['id'],b['bookingNo'],b['seatNo'],b['startTime'],b['endTime'],b['statusText'],b['canCancel'])) for b in d['list']]"

echo "T17 管理员查看张三预约详情（应成功）"
req GET "/api/bookings/$ID3" "$AD" | show
