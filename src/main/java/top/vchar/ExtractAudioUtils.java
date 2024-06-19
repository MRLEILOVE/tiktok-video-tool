package top.vchar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;


/**
 * 提取音频工具
 */
public class ExtractAudioUtils {

    /**
     * 使用FFmpeg从视频中提取音频。
     * <br/>
     * <pre>
     * ffmpeg -y -i input_video.mp4 -vn -c:a libmp3lame output_audio.mp3
     * 这里是对该命令的简单解释：
     * ffmpeg：这是调用FFmpeg程序本身。
     * -y 这个选项告诉FFmpeg覆盖已存在的输出文件，如果文件已存在，则覆盖它。
     * -i input_video.mp4：指定输入文件名，本例中是要提取音频的视频文件input_video.mp4。
     * -vn：这个选项告诉FFmpeg不要包括视频流，只处理音频。
     * -c:a libmp3lame：设置音频编码器为libmp3lame，用于将音频编码为MP3格式。如果你想要其他格式，比如AAC，可以将此选项替换为-c:a aac。
     * output_audio.mp3：指定输出的音频文件名及其格式，在这个例子中是MP3格式的output_audio.mp3。
     * 请根据你的具体需求调整输入文件名、输出文件名及格式。确保你的FFmpeg安装包含了libmp3lame库或者其他你想要使用的编码器库，否则可能会遇到编码器未找到的错误。
     * 此外，如果需要保持原始音频质量，可以考虑使用以下命令，它会尝试复制音频流而不仅仅是转码（这适用于输出格式支持原始音频编码的情况）
     * ffmpeg -i input_video.mp4 -vn -c copy output_audio.m4a
     * 在这个例子中，输出格式是.m4a，这是一种常见的音频容器格式，能够无损地存储原始音频流（假设原始视频的音频编码格式是AAC或其他.m4a容器兼容的格式）。
     * </pre>
     *
     * @param inputVideoPath  输入视频文件的路径。
     * @param outputAudioPath 输出音频文件的路径。
     * @param audioFormat     音频输出格式（例如："mp3"）。
     */
    public static void extractAudio(String inputVideoPath, String outputAudioPath, String audioFormat) {

        // 根据音频格式动态构建FFmpeg命令
        // ffmpeg -y -i "C:\IdeaProjects\tiktok-video-tool\1718700404659.mp4" -vn -c:a libmp3lame "C:\IdeaProjects\tiktok-video-tool\1718700404659.mp3"
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", inputVideoPath, "-vn", "-c:a", audioFormat.equals("mp3") ? "libmp3lame" : audioFormat, outputAudioPath);

        // 打印命令
        final List<String> command = pb.command();
        System.out.println("执行命令：");
        for (String s : command) {
            System.out.print(s + " ");
        }

        System.out.println();

        pb.redirectErrorStream(true); // 这一行将stderr重定向到stdout

        try {
            // 执行外部命令
            Process process = pb.start();

            // 读取并打印命令输出，可选，主要用于调试
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // 现在应该能看到来自stdout和stderr的所有输出
            }

            // 等待命令执行完成
            process.waitFor();
            System.out.println("音频提取完成");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String basePath = "C:\\IdeaProjects\\tiktok-video-tool";
        // 示例调用
        String inputPath = basePath + "\\1718775672995.mp4";
        String outputPath = basePath + "\\1718775672995.mp3"; // 注意这里的".mp3"是根据需要的格式动态添加的
        extractAudio(inputPath, outputPath, "mp3");
    }
}
