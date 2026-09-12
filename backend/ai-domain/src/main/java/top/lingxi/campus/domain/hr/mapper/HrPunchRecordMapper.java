package top.lingxi.campus.domain.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import org.apache.ibatis.annotations.Mapper;
import top.lingxi.campus.domain.hr.eneity.HrPunchRecord;

import java.time.LocalDate;

@Mapper
public interface HrPunchRecordMapper extends BaseMapper<HrPunchRecord> {

    /**
     * 查询用户某月某类型的补卡次数
     */
    default long countMonthly(Long userId, String punchType, LocalDate date) {
        QueryWrapper<HrPunchRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("punch_type", punchType)
                .ge("punch_date", date.withDayOfMonth(1))
                .le("punch_date", date.withDayOfMonth(date.lengthOfMonth()))
                .in("status", 1, 2); // 待处理或通过的都算
        return this.selectCount(wrapper);
    }
}