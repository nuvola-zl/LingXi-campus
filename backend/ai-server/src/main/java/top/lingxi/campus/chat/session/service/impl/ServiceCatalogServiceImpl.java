package top.lingxi.campus.chat.session.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.lingxi.campus.chat.session.service.IServiceCatalogService;
import top.lingxi.campus.domain.biz.catalog.entity.BizServiceCatalog;
import top.lingxi.campus.domain.biz.catalog.mapper.BizServiceCatalogMapper;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogServiceImpl implements IServiceCatalogService {

    private final BizServiceCatalogMapper catalogMapper;
// v3-removed: 关键词缓存组件已下线，CRUD 保留（管理端仍在使用，路由接口可复用此表）
//    private final CatalogKeywordService catalogKeywordService;
//    private final IntentKeywordService intentKeywordService;

    @Override
    public BizServiceCatalog create(BizServiceCatalog catalog) {
        catalogMapper.insert(catalog);
        // [v3-removed] catalogKeywordService.refresh();
        log.info("创建服务目录: id={}, name={}", catalog.getId(), catalog.getName());
        return catalogMapper.selectById(catalog.getId());
    }

    @Override
    public BizServiceCatalog update(Long id, BizServiceCatalog catalog) {
        catalog.setId(id);
        catalogMapper.updateById(catalog);
        // [v3-removed] catalogKeywordService.refresh();
        log.info("更新服务目录: id={}", id);
        return catalogMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        catalogMapper.deleteById(id);
        // [v3-removed] catalogKeywordService.refresh();
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
        // [v3-removed] 缓存组件已下线，本方法暂为空实现（保留接口兼容管理端调用）
        log.info("手动刷新服务目录缓存（缓存组件已下线，无需操作）");
    }
}