package top.lingxi.campus.domain.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceDetail;

@Mapper
public interface AdminDeviceDetailMapper extends BaseMapper<AdminDeviceDetail> {
    
    /**
     * 查询某类型在库设备
     */
    @Select("SELECT * FROM admin_device_detail WHERE device_type = #{deviceType} AND status = 1 LIMIT 1")
    AdminDeviceDetail findAvailable(@Param("deviceType") String deviceType);
}