package top.lingxi.campus.ai.service;

import top.lingxi.campus.result.WanxImageResult;

public interface IWanxImageService {
    WanxImageResult generateImage(String prompt, Long sessionId);
}