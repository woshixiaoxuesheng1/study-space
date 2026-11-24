package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.dto.PageResult;
import com.studyspace.dto.RoomRequest;
import com.studyspace.dto.RoomVO;
import com.studyspace.dto.SeatBatchRequest;
import com.studyspace.dto.SeatLayoutVO;
import com.studyspace.dto.SeatVO;
import com.studyspace.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 资源管理接口：自习室与座位（A-06 ~ A-13）
 * <p>读接口所有登录用户都能用；写接口只有管理员能调，
 * 权限拦在 SecurityConfig 的路径规则里，不在方法里再写一遍</p>
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /** A-06 自习室列表，可按状态、关键字筛选 */
    @GetMapping
    public Result<PageResult<RoomVO>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword) {
        return Result.success(roomService.pageRooms(page, size, status, keyword));
    }

    /** A-07 自习室详情 */
    @GetMapping("/{id}")
    public Result<RoomVO> detail(@PathVariable Long id) {
        return Result.success(roomService.getRoom(id));
    }

    /** A-08 新增自习室（管理员） */
    @PostMapping
    public Result<RoomVO> create(@Valid @RequestBody RoomRequest request) {
        return Result.success("新增成功", roomService.createRoom(request));
    }

    /** A-09 修改自习室（管理员） */
    @PutMapping("/{id}")
    public Result<RoomVO> update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return Result.success("修改成功", roomService.updateRoom(id, request));
    }

    /** A-10 删除自习室（管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return Result.success("删除成功", null);
    }

    /** A-11 座位布局和当前状态 */
    @GetMapping("/{id}/seats")
    public Result<SeatLayoutVO> seats(@PathVariable Long id) {
        return Result.success(roomService.getSeatLayout(id));
    }

    /** A-12 批量生成座位（管理员） */
    @PostMapping("/{id}/seats/batch")
    public Result<SeatLayoutVO> generateSeats(@PathVariable Long id,
                                              @Valid @RequestBody SeatBatchRequest request) {
        return Result.success("座位生成成功", roomService.generateSeats(id, request));
    }

    /** A-13 查某天某时段的空闲座位 */
    @GetMapping("/{id}/free-seats")
    public Result<List<SeatVO>> freeSeats(@PathVariable Long id,
                                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                          @RequestParam String startTime,
                                          @RequestParam String endTime) {
        return Result.success(roomService.listFreeSeats(id, date, startTime, endTime));
    }
}
