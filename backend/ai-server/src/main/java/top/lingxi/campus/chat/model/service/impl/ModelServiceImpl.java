package top.lingxi.campus.chat.model.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.lingxi.campus.common.constant.MessageConstant;
import top.lingxi.campus.common.context.BaseContext;
import top.lingxi.campus.domain.ai.dto.ModelDTO;
import top.lingxi.campus.domain.ai.entity.Model;
import top.lingxi.campus.domain.ai.mapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.List;

import top.lingxi.campus.chat.model.service.IModelService;

@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements IModelService {
    private final ModelMapper modelMapper;

    @Value("${haze.admin.id:1}")
    private Long adminId;

    @Override
    public void addModel(ModelDTO modelDTO) {
        if (modelDTO.getIsBeta() == null) {
            modelDTO.setIsBeta(false);
        }
        if (modelDTO.getIsRecommended() == null) {
            modelDTO.setIsRecommended(false);
        }
        if (modelDTO.getStatus() == null) {
            modelDTO.setStatus(true);
        }
        if (modelDTO.getSort() == null) {
            modelDTO.setSort(0);
        }

        Model model = BeanUtil.copyProperties(modelDTO, Model.class);
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        modelMapper.insert(model);
    }

    @Override
    public List<ModelDTO> listModels() {
        List<Model> models = modelMapper.selectList(new LambdaQueryWrapper<Model>()
                .eq(Model::getStatus, true)
                .orderByDesc(Model::getSort)
        );
        if (models == null || models.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return BeanUtil.copyToList(models, ModelDTO.class);
    }

    @Override
    public void deleteModel(Long id) {
        if (!BaseContext.getCurrentId().equals(adminId)) {
            throw new RuntimeException(MessageConstant.NOT_AUTHED_TO_DELETE);
        }
        modelMapper.deleteById(id);
    }
}
