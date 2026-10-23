# 拾序 1.0.12 实际回归记录

1052条核心断言、78条固定历史案例（250条断言）全部通过。

模拟器发布包完整基础组及通知、用户原图专项：

- core：PASS
- Noon：PASS
- SameTime：PASS
- BeforeDay：PASS
- Inline：PASS
- Snooze：PASS
- Ocr：PASS
- Batch：PASS
- WidgetPreview：PASS
- Notice：PASS
- NoticePhoto：PASS

私有通知图片已离线核对标题、日期、地点与完整时间段；备注保留识别原文。编辑前未自动保存，取消后未新增记录。

安装包与已测模拟器包DEX一致，完整本机OCR模型与旧版逐字节一致，签名保持原密钥。

本次曾因ADB连接中断失败，保留adb-interrupted-results.json及Inline-adb-interrupted.log；恢复连接后重跑完整组。
真机振动、锁屏延迟及小米桌面兼容性本轮未测。
原图只用于本机验证，不进入APK资源，原图未上传GitHub或公网。
