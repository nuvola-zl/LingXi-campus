package top.lingxi.campus.chat.session.service;

import top.lingxi.campus.domain.biz.catalog.entity.BizServiceCatalog;

import java.util.List;

public interface IServiceCatalogService {

    BizServiceCatalog create(BizServiceCatalog catalog);

    BizServiceCatalog update(Long id, BizServiceCatalog catalog);

    void delete(Long id);

    BizServiceCatalog getById(Long id);

    List<BizServiceCatalog> listAll();

    void refreshCache();
}