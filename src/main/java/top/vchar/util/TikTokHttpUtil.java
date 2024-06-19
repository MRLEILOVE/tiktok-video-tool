package top.vchar.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.brotli.dec.BrotliInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import top.vchar.AppUI;
import top.vchar.dto.RenderData;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p> 请求帮助类 </p>
 *
 * @author vchar fred
 * @version 1.0
 * @create_date 2020/11/23
 */
public class TikTokHttpUtil {


    // 连接超时时间 10s，单位为毫秒
    public static final int CONNECT_TIMEOUT = 10000;

    // 连接请求超时时间，单位为毫秒
    public static final int CONNECTION_REQUEST_TIMEOUT = 10000;

    // socket读取超时时间，单位为毫秒
    public static final int SOCKET_TIMEOUT = 10000;

    // 用户代理字符串，模拟iPhone浏览器请求
    private static final String USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1";

    // 接受的 MIME 类型，表示客户端希望服务器返回的资源类型
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";

    private final CloseableHttpClient httpClient;

    private final AppUI appUI;

    private TikTokHttpUtil(AppUI appUI) {
        CookieStore cookieStore = new BasicCookieStore();
        this.httpClient = HttpClientBuilder.create()
                .setDefaultCookieStore(cookieStore)
                .build();
        this.appUI = appUI;
    }

    private static volatile TikTokHttpUtil tikTokHttpUtil;

    /**
     * 单例模式获取TikTokHttpUtil的实例。
     * 使用双重检查锁定以确保线程安全地创建单例对象。
     * 参数appUI用于初始化TikTokHttpUtil实例，提供与应用UI相关的交互能力。
     *
     * @param appUI 应用程序UI接口，用于与TikTokHttpUtil的交互。
     * @return TikTokHttpUtil的单例实例。
     */
    public static TikTokHttpUtil getInstance(AppUI appUI) {
        // 检查实例是否已经存在，如果不存在则进行实例化。
        if (tikTokHttpUtil == null) {
            // 使用synchronized关键字确保线程安全，避免多个线程同时进入创建实例的代码块。
            synchronized (TikTokHttpUtil.class) {
                // 再次检查实例是否存在，因为可能在获取锁的过程中其他线程已经创建了实例。
                if (tikTokHttpUtil == null) {
                    // 创建TikTokHttpUtil的实例，传入appUI参数进行初始化。
                    tikTokHttpUtil = new TikTokHttpUtil(appUI);
                }
            }
        }
        // 返回TikTokHttpUtil的单例实例。
        return tikTokHttpUtil;
    }

    /**
     * 提取抖音无水印视频链接
     *
     * @param tikTokVideoShareUrl 抖音分享视频连接
     * @return 返回抖音无水印视频链接
     */
    public List<String> extractVideoUrl(String tikTokVideoShareUrl) throws Exception {
        //过滤链接，获取http连接地址
        String url = decodeHttpUrl(tikTokVideoShareUrl);
        List<String> urlList = execute(url);
        printLog(JSONObject.toJSONString(urlList));
        return urlList;
    }

    /**
     * 根据给定的URL执行请求，并解析返回的数据。
     * 如果URL以特定字符串开始，表示数据已经直接嵌入在URL中，直接解析该数据。
     * 否则，将发送HTTP GET请求，并根据响应的内容编码解码数据，然后解析解码后的数据。
     *
     * @param url 请求的URL。
     * @return 解析后的数据列表。
     * @throws Exception 如果请求或解析过程中发生错误。
     */
    public List<String> execute(String url) throws Exception {
        // 检查URL是否以特定字符串开始，如果是，则直接解析数据
        if (url.startsWith("<html")) {
            return this.parseData(url);
        }

        // 创建HTTP GET请求并设置请求的URI和超时配置
        HttpGet httpGet = new HttpGet(buildUri(url));
        httpGet.setConfig(setHttpTimeOut());

        // 设置请求头信息，包括User-Agent、Accept、Accept-Encoding和accept-language
        httpGet.addHeader("User-Agent", USER_AGENT);
        httpGet.addHeader("Accept", ACCEPT);
        httpGet.addHeader("Accept-Encoding", "gzip, deflate, br, zstd");
        httpGet.addHeader("accept-language", "zh-CN,zh;q=0.9");

        // 创建Cookie存储并添加一个空的Cookie，用于维护会话状态
        BasicCookieStore cookieStore = new BasicCookieStore();
        Cookie cookieExample = new BasicClientCookie("", "");
        cookieStore.addCookie(cookieExample);

        // 创建HTTP上下文，并设置Cookie存储
        HttpClientContext context = HttpClientContext.create();

        // 使用CloseableHttpClient发送请求并处理响应
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build()) {
            HttpResponse response = httpclient.execute(httpGet, context);
            try {
                // 检查响应的内容编码是否为br，如果是，则使用Brotli解压缩数据
                if (response.getFirstHeader("Content-Encoding") != null
                        && "br".equalsIgnoreCase(response.getFirstHeader("Content-Encoding").getValue())) {
                    byte[] body = decompressBrotli(EntityUtils.toByteArray(response.getEntity()));
                    return this.parseData(new String(body));
                } else {
                    // 否则，直接将响应实体转换为字符串并解析
                    return this.parseData(EntityUtils.toString(response.getEntity()));
                }
            } finally {
                // 确保响应实体被完全消费，避免资源泄漏
                EntityUtils.consume(response.getEntity());
            }
        } catch (IOException e) {
            // 记录IO异常日志，并抛出NullPointerException，提供更明确的错误信息
            printLog("提取视频下载地址异常!");
            throw new NullPointerException("提取视频下载地址异常!");
        }
    }

    /**
     * 解析HTML字符串以获取渲染数据中的URL列表。
     * 使用Jsoup库解析HTML，定位特定ID的元素，对该元素的内容进行解码，并将其转换为RenderData对象，
     * 最终返回URL列表。
     *
     * @param html 待解析的HTML字符串。
     * @return RenderData对象中包含的URL列表。
     */
    private List<String> parseData(String html) {
        // 使用Jsoup解析HTML字符串
        Document document = Jsoup.parse(html);
        // 获取ID为"RENDER_DATA"的元素
        Element element = document.getElementById("RENDER_DATA");
        // 对元素内容进行URL解码，并指定字符集为UTF-8
        String str = URLDecoder.decode(element.html(), StandardCharsets.UTF_8);
        // 将解码后的字符串转换为RenderData对象
        RenderData renderData = JSONObject.parseObject(str, RenderData.class);
        // 返回RenderData对象中的URL列表
        return renderData.getUrls();
    }


    /**
     * 使用Brotli解压缩给定的字节数据。
     * <p>
     * 此方法通过BrotliInputStream读取压缩后的字节数据，并将其解压缩后写入ByteArrayOutputStream。
     * 使用try-with-resources语句确保资源在使用后能被正确关闭。
     *
     * @param compressed 压缩后的字节数据。
     * @return 解压缩后的字节数据。
     * @throws IOException 如果在读取或写入过程中发生I/O错误。
     */
    private static byte[] decompressBrotli(byte[] compressed) throws IOException {
        // 使用BrotliInputStream对压缩数据进行解压缩，ByteArrayOutputStream用于收集解压缩后的数据
        try (BrotliInputStream brotliInputStream = new BrotliInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // 用于临时存储解压缩数据的缓冲区
            byte[] buffer = new byte[1024];
            int length;
            // 循环读取解压缩数据，直到没有更多数据可读
            while ((length = brotliInputStream.read(buffer)) != -1) {
                // 将读取到的解压缩数据写入到输出流中
                outputStream.write(buffer, 0, length);
            }
            // 返回解压缩后的全部数据
            return outputStream.toByteArray();
        }
    }


    /**
     * 解码HTTP URL。
     * 该方法主要用于处理和解析来自抖音等平台的分享链接，以获取实际的URL。
     * 如果传入的字符串是以<html开头，则认为它是一个HTML页面，不进行处理。
     * 否则，将尝试解析出URL中的有效部分，用于后续处理或展示。
     *
     * @param url 待解码的URL字符串。
     * @return 解码后的URL字符串。如果输入字符串以<html开头，则返回原字符串。
     * @throws NullPointerException 如果解析过程中出现异常，则抛出空指针异常。
     */
    public String decodeHttpUrl(String url) {
        // 检查传入的字符串是否为HTML页面
        // 判断传入的是否是一个HTML网页内容
        if (url.startsWith("<html")) {
            // 如果是HTML页面，则不进行处理，直接返回原字符串
            printLog("非抖音分享链接，使用网页解析");
            if (appUI != null) {
                appUI.updateUrl("");
            }
            return url;
        }

        try {
            // 查找http在字符串中的位置，以确定URL的开始
            int start = url.indexOf("http");
            // 从http开始提取整个URL
            String dyUri = url.substring(start);
            // 尝试找到第一个/空格前的位置，作为URL的结束位置
            int end = dyUri.indexOf("/ ");
            // 如果找不到空格，则取最后一个斜杠的位置作为URL的结束位置
            if (end < 1) {
                end = dyUri.lastIndexOf("/");
            }
            // 提取有效的URL部分
            String relUrl = dyUri.substring(0, end + 1);

            // 更新UI中的URL显示
            if (appUI != null) {
                appUI.updateUrl(relUrl);
            }
            // 返回处理后的URL
            return relUrl;
        } catch (Exception e) {
            // 记录解析失败的日志
            printLog("解析抖音视频分享链接失败.");
            // 抛出空指针异常，以通知调用者解析失败
            throw new NullPointerException("提取视频下载地址异常!");
        }

    }


    /**
     * 设置请求超时
     *
     * @return 返回新请求配置
     */
    private static RequestConfig setHttpTimeOut() {
        return RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();
    }

    /**
     * 根据给定的URL构建URI对象。
     * 此方法用于验证输入的URL是否可以构建为有效的URI，如果URL格式不正确，则抛出异常。
     *
     * @param url 待构建URI的字符串表示。必须是一个有效的URL。
     * @return 构建成功的URI对象。
     * @throws Exception 如果URL格式不正确，或者无法构建URI，则抛出异常。
     */
    private URI buildUri(String url) throws Exception {
        try {
            // 使用URIBuilder尝试构建URI，如果URL格式正确，则此步骤将成功。
            URIBuilder uriBuilder = new URIBuilder(url);
            return uriBuilder.build();
        } catch (URISyntaxException e) {
            // 如果URL格式不正确，捕获URISyntaxException异常。
            // 通过日志记录错误信息，然后重新抛出一个通用的Exception异常。
            printLog(String.format("请求地址[%s]不正确", url));
            throw new Exception(e);
        }
    }


    /**
     * 下载视频。
     * 通过给定的视频URL，将视频下载到指定的目录，并以指定的文件名保存。
     * 使用Jsoup库来处理视频链接的下载。
     *
     * @param videoUrl 视频的URL地址。
     * @param dir      下载保存的目录路径。
     * @param fileName 下载保存的文件名。
     */
    public void downloadVideo(String videoUrl, String dir, String fileName) {
        // 打印日志信息，记录视频链接。
        printLog("抖音去水印链接:" + videoUrl);

        // 初始化请求头信息，用于模拟浏览器访问。
        Map<String, String> headers = new HashMap<>(3);
        headers.put("Connection", "keep-alive");
        headers.put("Host", "aweme.snssdk.com");
        headers.put("User-Agent", USER_AGENT);

        OutputStream out = null;
        BufferedInputStream in = null;
        try {
            // 连接到视频URL，设置请求头和超时时间，并获取视频的输入流。
            in = Jsoup.connect(videoUrl).headers(headers).referrer("https://www.iesdouyin.com/").timeout(20000).ignoreContentType(true).execute().bodyStream();
            // 构建文件输出流，准备写入视频数据。
            out = buildFileOutOutStream(dir, fileName);
            // 逐字节读取视频数据，并写入到输出流中，完成视频的下载。
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            // 下载成功后，打印日志信息。
            printLog("视频下载成功");
        } catch (IOException e) {
            // 下载过程中出现异常，打印日志信息，并尝试重新下载。
            printLog("视频下载失败: " + e.getMessage());
            // 由于jsoup偶尔会出现失败的情况，这里切换到httpclient
            retryDownloadVideo(videoUrl, dir, fileName);
        } finally {
            // 关闭输入输出流，确保资源的释放。
            if (out != null) {
                try {
                    //关闭输出流
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (in != null) {
                try {
                    //关闭输入流
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * 重试下载视频的方法，尝试通过构建不同的请求来下载视频，如果失败则记录日志。
     *
     * @param videoUrl 视频的URL地址。
     * @param dir      保存视频的目录。
     * @param fileName 视频的文件名。
     */
    private void retryDownloadVideo(String videoUrl, String dir, String fileName) {
        // 打印日志，表示开始尝试通过其他方式下载视频
        printLog("开始尝试通过其他方式下载视频，若依然失败请手动下载");
        try {
            // 创建HttpGet请求，设置请求的URI和超时时间
            HttpGet httpGet = new HttpGet(buildUri(videoUrl));
            httpGet.setConfig(setHttpTimeOut());

            // 添加请求头信息，模拟浏览器访问
            httpGet.addHeader("User-Agent", "PostmanRuntime/7.29.2");
            httpGet.addHeader("Accept", "*/*");
            httpGet.addHeader("Host", "aweme.snssdk.com");
            httpGet.addHeader("Accept-Encoding", "gzip, deflate, br");
            httpGet.addHeader("Connection", "keep-alive");

            // 创建上下文对象
            HttpClientContext context = HttpClientContext.create();
            // 执行HTTP请求，获取响应
            try (CloseableHttpResponse ignored = httpClient.execute(httpGet, context)) {
                // 获取重定向的URI
                URI uri = context.getRedirectLocations().get(0);
                // 打印重定向后的URI
                printLog("抖音去水印链接:" + uri.toString());

                // 创建新的HttpGet请求，针对重定向的URI
                HttpGet httpGet2 = new HttpGet(uri);
                httpGet2.setConfig(setHttpTimeOut());

                // 添加更详细的请求头信息，包括User-Agent和Accept等
                httpGet2.addHeader("User-Agent", USER_AGENT);
                httpGet2.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
                httpGet2.addHeader("Accept-Encoding", "gzip, deflate, br");
                httpGet2.addHeader("Connection", "keep-alive");

                // 创建新的上下文对象
                HttpClientContext context2 = HttpClientContext.create();
                // 执行第二次HTTP请求，获取响应
                try (CloseableHttpResponse response = httpClient.execute(httpGet2, context2)) {
                    // 检查响应状态码，如果为200表示成功
                    if (response.getStatusLine().getStatusCode() == 200) {
                        // 获取响应的输入流
                        InputStream in = response.getEntity().getContent();
                        // 构建文件输出流
                        try (OutputStream out = buildFileOutOutStream(dir, fileName)) {
                            // 逐字节读取输入流，并写入输出流，完成视频下载
                            int b;
                            while ((b = in.read()) != -1) {
                                out.write(b);
                            }
                            // 打印日志，表示视频下载成功
                            printLog("视频下载成功");
                        }
                    } else {
                        // 打印日志，表示视频下载失败，并记录响应状态码
                        printLog("视频下载失败：" + response.getStatusLine());
                    }
                }
            } catch (IOException e) {
                // 打印日志，表示尝试重新下载失败，并记录异常信息
                printLog("尝试重新下载失败: " + e.getMessage());
            }
        } catch (Exception e) {
            // 打印日志，表示下载地址解析失败
            printLog("下载地址解析失败!");
        }
    }


    /**
     * 下载图片。
     * 使用Jsoup库从指定的imageUrl下载图片，并保存到指定的目录dir中。
     *
     * @param imageUrl 图片的URL地址。
     * @param dir      图片保存的目录路径。
     */
    public void downloadImage(String imageUrl, String dir) {
        // 初始化请求头信息，模拟浏览器访问
        Map<String, String> headers = new HashMap<>();
        headers.put("Connection", "keep-alive");
        headers.put("Host", "aweme.snssdk.com");
        headers.put("User-Agent", USER_AGENT);

        OutputStream out = null;
        BufferedInputStream in = null;
        try {
            // 连接到图片URL，设置请求头和超时时间，并获取图片的输入流
            in = Jsoup.connect(imageUrl).headers(headers).timeout(20000)
                    .ignoreContentType(true)
                    .execute()
                    .bodyStream();

            // 生成唯一的文件名，以当前时间戳命名
            String fileName = System.currentTimeMillis() + ".jpeg";
            // 构建文件保存的完整路径
            File fileSavePath = new File(dir + fileName);
            // 获取文件保存路径的父目录，并检查是否存在，如果不存在则尝试创建
            File fileParent = fileSavePath.getParentFile();
            if (!fileParent.exists()) {
                if (!fileParent.mkdirs()) {
                    // 如果父目录创建失败，打印日志并返回
                    printLog("文件创建失败，请检查文件路径是否正确");
                    return;
                }
            }

            // 打开文件输出流，准备写入图片数据
            out = new BufferedOutputStream(Files.newOutputStream(fileSavePath.toPath()));
            // 读取图片数据，并写入到输出流中
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }

            // 下载成功后，打印日志记录
            printLog("图片下载成功:" + imageUrl);
            printLog("图片保存路径:" + fileSavePath.getAbsolutePath() + "\n");
        } catch (IOException e) {
            // 如果下载过程中发生异常，打印错误日志
            printLog("图片下载失败: " + e.getMessage());
        } finally {
            // 关闭输出流
            if (out != null) {
                try {
                    //关闭输出流
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 关闭输入流
            if (in != null) {
                try {
                    //关闭输入流
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void printLog(String message) {
        if (this.appUI != null) {
            appUI.addLog(message);
        } else {
            System.out.println(message);
        }
    }

    /**
     * 构建一个输出流，用于写入文件。
     * 如果未指定文件名，则使用当前时间戳作为文件名。
     * 如果文件夹不存在，则尝试创建文件夹。
     *
     * @param dir      文件保存的目录。
     * @param fileName 文件名，可以为null。
     * @return 返回一个输出流，用于向指定文件写入数据。
     * @throws IOException 如果文件创建或打开失败。
     */
    private OutputStream buildFileOutOutStream(String dir, String fileName) throws IOException {
        // 如果文件名未指定，则使用当前时间戳作为文件名
        if (null == fileName) {
            fileName = System.currentTimeMillis() + ".mp4";
        }
        // 构建文件的完整路径
        File fileSavePath = new File(dir + fileName);
        // 获取文件的父目录
        File fileParent = fileSavePath.getParentFile();
        // 如果父目录不存在，则尝试创建父目录
        if (!fileParent.exists()) {
            if (!fileParent.mkdirs()) {
                // 如果创建父目录失败，则记录日志并抛出异常
                printLog("文件创建失败，请检查文件路径是否正确");
                throw new IOException("文件创建失败，请检查文件路径是否正确");
            }
        }
        // 记录文件保存路径的日志
        printLog("视频保存路径:" + fileSavePath.getAbsolutePath());
        // 返回一个缓冲的文件输出流
        return new BufferedOutputStream(Files.newOutputStream(fileSavePath.toPath()));
    }

}
