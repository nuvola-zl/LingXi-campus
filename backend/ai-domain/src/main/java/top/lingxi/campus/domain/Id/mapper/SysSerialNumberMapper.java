package top.lingxi.campus.domain.Id.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.Id.eneity.SysSerialNumber;

@Mapper
public interface SysSerialNumberMapper extends BaseMapper<SysSerialNumber> {
    
    /**
     * 原子生成单号
     */
    @Select("""
        INSERT INTO sys_serial_number (prefix, curr_date, current_no)
        VALUES (#{prefix}, CURRENT_DATE, 1)
        ON CONFLICT (prefix) DO UPDATE
        SET 
            current_no = CASE 
                WHEN sys_serial_number.curr_date = CURRENT_DATE THEN sys_serial_number.current_no + 1
                ELSE 1
            END,
            curr_date = CURRENT_DATE
        RETURNING #{prefix} || '-' || TO_CHAR(curr_date, 'YYYYMMDD') || '-' || LPAD(current_no::TEXT, 3, '0')
        """)
    String generateNo(@Param("prefix") String prefix);
}