package top.lingxi.campus.chat.model.service;

import top.lingxi.campus.domain.ai.dto.ModelDTO;

import java.util.List;

public interface IModelService {

    /**
     * 新增模型信息
     * @param modelDTO
     */
    void addModel(ModelDTO modelDTO);

    /**
     * 获取可用模型列表
     * @return
     */
    List<ModelDTO> listModels();

    /**
     * 删除模型信息
     * @param id
     */
    void deleteModel(Long id);
}
