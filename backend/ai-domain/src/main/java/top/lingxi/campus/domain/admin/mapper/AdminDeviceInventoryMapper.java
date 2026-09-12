package top.lingxi.campus.domain.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.lingxi.campus.domain.admin.eneity.AdminDeviceInventory;

@Mapper
public interface AdminDeviceInventoryMapper extends BaseMapper<AdminDeviceInventory> {

    @Select("SELECT * FROM admin_device_inventory WHERE device_type = #{type} FOR UPDATE")
    AdminDeviceInventory selectForUpdate(@Param("type") String type);
}