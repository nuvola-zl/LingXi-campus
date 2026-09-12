package top.lingxi.campus.chat.session.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.lingxi.campus.chat.session.service.IServiceCatalogService;
import top.lingxi.campus.ai.service.impl.IntentKeywordService;
import top.lingxi.campus.rag.cache.CatalogKeywordService;
import top.lingxi.campus.domain.biz.catalog.entity.BizServiceCatalog;
import top.lingxi.campus.domain.biz.catalog.mapper.BizServiceCatalogMapper;


import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogServiceImpl implements IServiceCatalogService {

    private final BizServiceCatalogMapper catalogMapper;
    private final CatalogKeywordService catalogKeywordService;
    private final IntentKeywordService intentKeywordService;  // 新增

    @Override
    public BizServiceCatalog create(BizServiceCatalog catalog) {
        catalogMapper.insert(catalog);
        // 新增后刷新缓存，立即生效
        catalogKeywordService.refresh();
        log.info("创建服务目录: id={}, name={}", catalog.getId(), catalog.getName());
        return catalogMapper.selectById(catalog.getId());
    }

    @Override
    public BizServiceCatalog update(Long id, BizServiceCatalog catalog) {
        catalog.setId(id);
        catalogMapper.updateById(catalog);
        catalogKeywordService.refresh();
        log.info("更新服务目录: id={}", id);
        return catalogMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        catalogMapper.deleteById(id);
        catalogKeywordService.refresh();
        log.info("删除服务目录: id={}", id);
    }

    @Override
    public BizServiceCatalog getById(Long id) {
        return catalogMapper.selectById(id);
    }

    @Override
    public List<BizServiceCatalog> listAll() {
        return catalogMapper.selectAllWithCategory();
    }

    @Override
    public void refreshCache() {
        catalogKeywordService.refresh();
        intentKeywordService.refresh();  // 加这一行
        log.info("手动刷新服务目录缓存");
    }
}