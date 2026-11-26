package com.studyspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyspace.cache.RoomListCacheStore;
import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import com.studyspace.constants.BookingConstants;
import com.studyspace.constants.CommonConstants;
import com.studyspace.constants.RoomConstants;
import com.studyspace.dto.PageResult;
import com.studyspace.dto.RoomRequest;
import com.studyspace.vo.RoomVO;
import com.studyspace.dto.SeatBatchRequest;
import com.studyspace.vo.SeatLayoutVO;
import com.studyspace.dto.SeatOccupancy;
import com.studyspace.vo.SeatVO;
import com.studyspace.entity.Room;
import com.studyspace.entity.Seat;
import com.studyspace.mapper.BookingMapper;
import com.studyspace.mapper.RoomMapper;
import com.studyspace.mapper.SeatMapper;
import com.studyspace.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自习室与座位业务实现
 * <p>座位显示状态由 booking 表实时算，不依赖 seat.status 字段——
 * 因为预约模块还没写，seat.status 不会自动变，光看字段会出现"有人正在用却显示空闲"的假象</p>
 * <p>自习室列表走 Redis 缓存（Cache-Aside）：读先查缓存，未命中查库回填；写改完数据库、
 * 事务提交后清缓存。座位布局、空闲座位这类带实时状态的接口不缓存，避免读到过期的占用情况</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final BookingMapper bookingMapper;
    private final RoomListCacheStore roomListCache;

    @Override
    public PageResult<RoomVO> pageRooms(int page, int size, String status, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), CommonConstants.MAX_PAGE_SIZE);
        String key = StringUtils.hasText(keyword) ? keyword.trim() : null;

        // 命中缓存就直接返回，一条 SQL 都不发（连续请求列表页时，只有第一次会看到 mapper 的 SQL 日志）
        String cacheKey = roomListCache.key(safePage, safeSize, status, key);
        PageResult<RoomVO> cached = roomListCache.get(cacheKey);
        if (cached != null) {
            log.debug("自习室列表命中缓存：key={}", cacheKey);
            return cached;
        }
        log.info("自习室列表未命中缓存，回源数据库：key={}", cacheKey);

        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<Room>()
                .eq(StringUtils.hasText(status), Room::getStatus, status)
                .and(key != null, w -> w.like(Room::getName, key).or().like(Room::getBuilding, key))
                .orderByAsc(Room::getId);

        long total = roomMapper.selectCount(wrapper);
        PageResult<RoomVO> result;
        if (total == 0) {
            // 空结果也回填：查不到数据的关键字通常是用户手快打错的，缓存住可以挡住重复穿透
            result = PageResult.empty(safePage, safeSize);
        } else {
            // 没有引入分页插件（要多一个 jsqlparser 依赖），这里用 LIMIT 手动分页；
            // 两个数字都是后端算好的 int，不存在拼接注入的问题
            wrapper.last("LIMIT " + (long) (safePage - 1) * safeSize + ", " + safeSize);
            List<RoomVO> list = roomMapper.selectList(wrapper).stream().map(RoomVO::from).toList();
            result = new PageResult<>(total, safePage, safeSize, list);
        }

        roomListCache.put(cacheKey, result);
        return result;
    }

    @Override
    public RoomVO getRoom(Long roomId) {
        return RoomVO.from(requireRoom(roomId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomVO createRoom(RoomRequest request) {
        checkTimeRange(request);
        checkNameUnique(request.getName().trim(), request.getBuilding().trim(), null);

        Room room = new Room();
        fill(room, request);
        room.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : RoomConstants.ROOM_STATUS_OPEN);
        room.setSeatCount(0);
        roomMapper.insert(room);
        evictListCache();

        log.info("新增自习室：id={}, name={}", room.getId(), room.getName());
        return RoomVO.from(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomVO updateRoom(Long roomId, RoomRequest request) {
        Room room = requireRoom(roomId);
        checkTimeRange(request);
        checkNameUnique(request.getName().trim(), request.getBuilding().trim(), roomId);

        fill(room, request);
        if (StringUtils.hasText(request.getStatus())) {
            room.setStatus(request.getStatus());
        }
        roomMapper.updateById(room);
        evictListCache();

        log.info("修改自习室：id={}, name={}", room.getId(), room.getName());
        return RoomVO.from(requireRoom(roomId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoom(Long roomId) {
        Room room = requireRoom(roomId);

        // 有人正订着或正在用这间自习室，删了会留下指向不存在房间的预约单
        if (bookingMapper.countActiveByRoom(roomId) > 0) {
            throw new BusinessException(ResultCode.ROOM_HAS_ACTIVE_BOOKING);
        }

        roomMapper.deleteById(roomId);
        // 座位跟着一起逻辑删除，页面上查这间房的座位就查不到了
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, roomId));
        evictListCache();

        log.info("删除自习室（逻辑删除）：id={}, name={}", roomId, room.getName());
    }

    @Override
    public SeatLayoutVO getSeatLayout(Long roomId) {
        Room room = requireRoom(roomId);
        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getRoomId, roomId)
                .orderByAsc(Seat::getRowNo)
                .orderByAsc(Seat::getColNo));

        // 传两个 now：条件变成 start_time < now AND end_time > now，正好是"此刻正在占用"
        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> occupancy = occupancyMap(roomId, now, now);

        List<SeatVO> seatVos = seats.stream()
                .map(seat -> SeatVO.from(seat, resolveStatus(seat, occupancy)))
                .toList();

        SeatLayoutVO layout = new SeatLayoutVO();
        layout.setRoomId(room.getId());
        layout.setRoomName(room.getName());
        layout.setRowCount(seats.stream().mapToInt(Seat::getRowNo).max().orElse(0));
        layout.setColCount(seats.stream().mapToInt(Seat::getColNo).max().orElse(0));
        layout.setSeatCount(seats.size());
        layout.setSeats(seatVos);
        return layout;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeatLayoutVO generateSeats(Long roomId, SeatBatchRequest request) {
        Room room = requireRoom(roomId);
        int rows = request.getRows();
        int cols = request.getCols();

        long existing = seatMapper.selectCount(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, roomId));
        if (existing > 0) {
            // 已经有人订过座位，不能推倒重来，否则预约单会指向已被删除的座位
            if (bookingMapper.countAllByRoom(roomId) > 0) {
                throw new BusinessException(ResultCode.ROOM_SEAT_LOCKED);
            }
            log.info("自习室 {} 原有 {} 个座位且无预约记录，重新生成", roomId, existing);
            seatMapper.physicalDeleteByRoomId(roomId);
        }

        List<Seat> seats = new ArrayList<>(rows * cols);
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                Seat seat = new Seat();
                seat.setRoomId(roomId);
                // 与 init.sql 同一套编号规则：行转字母 + 两位列号，如 A01
                seat.setSeatNo(String.valueOf((char) ('A' + r - 1)) + String.format("%02d", c));
                seat.setRowNo(r);
                seat.setColNo(c);
                seat.setSeatType(seatType(c, cols));
                seat.setStatus(RoomConstants.SEAT_STATUS_FREE);
                seats.add(seat);
            }
        }
        seats.forEach(seatMapper::insert);

        // 座位总数回写自习室，列表页直接显示，不用每次 count
        room.setSeatCount(seats.size());
        roomMapper.updateById(room);
        // 座位数变了，列表页显示的数字也要跟着变
        evictListCache();

        log.info("自习室 {} 生成座位完成：{} 行 × {} 列 = {} 个", roomId, rows, cols, seats.size());
        return getSeatLayout(roomId);
    }

    @Override
    public List<SeatVO> listFreeSeats(Long roomId, LocalDate date, String startTime, String endTime) {
        Room room = requireRoom(roomId);
        if (!RoomConstants.ROOM_STATUS_OPEN.equals(room.getStatus())) {
            throw new BusinessException(ResultCode.ROOM_CLOSED);
        }

        LocalDateTime start = LocalDateTime.of(date, parseTime(startTime));
        LocalDateTime end = LocalDateTime.of(date, parseTime(endTime));
        if (!start.isBefore(end)) {
            throw new BusinessException(ResultCode.BOOKING_TIME_INVALID);
        }
        if (start.toLocalTime().isBefore(room.getOpenTime()) || end.toLocalTime().isAfter(room.getCloseTime())) {
            throw new BusinessException(ResultCode.OUT_OF_OPEN_TIME,
                    "查询时间需在开放时间 " + room.getOpenTime() + " - " + room.getCloseTime() + " 之内");
        }

        // 停用的座位不参与查询（坏了/维修中）
        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getRoomId, roomId)
                .ne(Seat::getStatus, RoomConstants.SEAT_STATUS_DISABLED)
                .orderByAsc(Seat::getRowNo)
                .orderByAsc(Seat::getColNo));

        Map<Long, String> occupancy = occupancyMap(roomId, start, end);
        return seats.stream()
                .filter(seat -> !occupancy.containsKey(seat.getId()))
                .map(seat -> SeatVO.from(seat, RoomConstants.SEAT_STATUS_FREE))
                .toList();
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Room requireRoom(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException(ResultCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    /**
     * 清自习室列表缓存，挂在事务提交之后执行
     * <p>提交前删掉的话，读线程很可能在新数据落库前就回源、把旧数据重新写回缓存，
     * 而它读到的旧数据又被后面提交的事务覆盖了，缓存会一直脏到 TTL 过期。
     * 所以统一等提交成功再删；事务回滚时缓存本来就该保持原样，没必要动</p>
     */
    private void evictListCache() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            roomListCache.evictAll();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                roomListCache.evictAll();
            }
        });
    }

    private void fill(Room room, RoomRequest request) {
        room.setName(request.getName().trim());
        room.setBuilding(request.getBuilding().trim());
        room.setFloor(request.getFloor());
        room.setOpenTime(request.getOpenTime());
        room.setCloseTime(request.getCloseTime());
        room.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
    }

    private void checkTimeRange(RoomRequest request) {
        if (!request.getOpenTime().isBefore(request.getCloseTime())) {
            throw new BusinessException(ResultCode.ROOM_TIME_INVALID);
        }
    }

    /** 同一楼栋下不允许同名，否则学生找"图书馆三楼自习室"会看到两个一模一样的 */
    private void checkNameUnique(String name, String building, Long excludeId) {
        Long count = roomMapper.selectCount(new LambdaQueryWrapper<Room>()
                .eq(Room::getName, name)
                .eq(Room::getBuilding, building)
                .ne(excludeId != null, Room::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.ROOM_NAME_EXISTS);
        }
    }

    /** 座位类型规则与 init.sql 一致：第一列靠窗，最后一列带插座，其余普通 */
    private String seatType(int col, int cols) {
        if (col == 1) {
            return RoomConstants.SEAT_TYPE_WINDOW;
        }
        return col == cols ? RoomConstants.SEAT_TYPE_POWER : RoomConstants.SEAT_TYPE_NORMAL;
    }

    /** 把预约记录转成"座位 id → 占用状态"，同一座位多条时使用中优先 */
    private Map<Long, String> occupancyMap(Long roomId, LocalDateTime start, LocalDateTime end) {
        Map<Long, String> map = new HashMap<>();
        for (SeatOccupancy item : bookingMapper.selectOccupancy(roomId, start, end)) {
            String old = map.get(item.getSeatId());
            if (old == null || BookingConstants.STATUS_CHECKED_IN.equals(item.getStatus())) {
                map.put(item.getSeatId(), item.getStatus());
            }
        }
        return map;
    }

    private String resolveStatus(Seat seat, Map<Long, String> occupancy) {
        if (RoomConstants.SEAT_STATUS_DISABLED.equals(seat.getStatus())) {
            return RoomConstants.SEAT_STATUS_DISABLED;
        }
        String bookingStatus = occupancy.get(seat.getId());
        if (BookingConstants.STATUS_CHECKED_IN.equals(bookingStatus)) {
            return RoomConstants.SEAT_STATUS_IN_USE;
        }
        return BookingConstants.STATUS_PENDING_CHECKIN.equals(bookingStatus)
                ? RoomConstants.SEAT_STATUS_BOOKED
                : RoomConstants.SEAT_STATUS_FREE;
    }

    /** 兼容 "10:00" 和 "10:00:00" 两种写法 */
    private LocalTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "时间不能为空，格式为 HH:mm");
        }
        try {
            String value = text.trim();
            return LocalTime.parse(value.length() == 5 ? value + ":00" : value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "时间格式应为 HH:mm");
        }
    }
}
