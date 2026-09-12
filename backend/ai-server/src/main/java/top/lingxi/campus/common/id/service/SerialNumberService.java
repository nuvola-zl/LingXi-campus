package top.lingxi.campus.common.id.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.lingxi.campus.domain.Id.mapper.SysSerialNumberMapper;

@Service
public class SerialNumberService {
    
    @Autowired
    private SysSerialNumberMapper serialMapper;
    
    /**
     * 生成业务单号
     * @param prefix LEAVE/FINE/MR/DEV/PO
     */
    public String generate(String prefix) {
        return serialMapper.generateNo(prefix);
    }
}