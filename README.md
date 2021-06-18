# tiktok-video-tool

抖音分享视频无水印下载工具；仅供学习使用；下载已经打包好的文件直接双击运行即可（注意需要安装jdk）

### 使用

* 直接点击抖音的分享按钮下面的`复制链接`，然后将其输入即可，点击下载将开始视频的下载；
* 从抖音复制过来的文本无需特殊处理，程序会自动识别；比如复制的内容如下：

```text
xxxx  %抖音原创动漫  @抖音动漫   %轻漫计划 %充能计划  https://v.douyin.com/eQL7SMb/ 緮制此lian接，打kaiDou音搜索，直接观看视頻！
```

直接使用这个复制内容即可

* 如果出现 `提取视频下载地址异常！` 提示建议等待几秒钟后再试

<img width="482" height="392" src="data:image/png;base64,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">

