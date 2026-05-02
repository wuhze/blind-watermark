package com.blindwatermark.service;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.common.BusinessException;
import com.blindwatermark.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

@Service
@RequiredArgsConstructor
public class BatchWatermarkService {

    private final BlindWatermarkService watermarkService;
    private final FileService fileService;
    private final AppProperties properties;

    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png");

    private static final int MIN_CONCURRENCY = 1;
    private static final int MAX_CONCURRENCY = 6;
    private static final long BASE_MEMORY = 300L * 1024 * 1024;
    private static final long PER_PIXEL_MEMORY = 15L;
    private static final long DCT_OVERHEAD = 10L * 1024 * 1024;
    private static final double MEMORY_SAFETY = 0.55;

    public static class FileResult {
        private String fileName;
        private String status;
        private String outputPath;
        private String error;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getOutputPath() { return outputPath; }
        public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class BatchResult {
        private int total;
        private int success;
        private int failed;
        private long elapsedMs;
        private List<FileResult> results = new ArrayList<>();

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getSuccess() { return success; }
        public void setSuccess(int success) { this.success = success; }
        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }
        public long getElapsedMs() { return elapsedMs; }
        public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
        public List<FileResult> getResults() { return results; }
    }

    public BatchResult batchEmbed(String sourceDir, String outputDir,
                                   String watermarkText, int strength, String password) {
        File dir = validateDirectory(sourceDir);
        String outDir = resolveOutputDir(outputDir, "batch_embed");
        new File(outDir).mkdirs();

        File[] imageFiles = listImageFiles(dir);
        int total = imageFiles.length;

        BatchResult result = new BatchResult();
        result.setTotal(total);
        long startTime = System.currentTimeMillis();

        long maxMem = Runtime.getRuntime().maxMemory();
        long availableMem = (long) ((maxMem - BASE_MEMORY) * MEMORY_SAFETY);

        long totalPixels = 0;
        int sampled = 0;
        int sampleStep = Math.max(1, total / 10);
        for (int i = 0; i < total; i += sampleStep) {
            int[] dim = readImageDimension(imageFiles[i]);
            if (dim != null) {
                totalPixels += (long) dim[0] * dim[1];
                sampled++;
            }
        }
        long avgPixels = sampled > 0 ? totalPixels / sampled : 0;
        long estimatedPeakPerImage = avgPixels * PER_PIXEL_MEMORY + DCT_OVERHEAD;
        if (estimatedPeakPerImage < 50L * 1024 * 1024) {
            estimatedPeakPerImage = 50L * 1024 * 1024;
        }

        int concurrency = (int) Math.max(MIN_CONCURRENCY, Math.min(MAX_CONCURRENCY, availableMem / estimatedPeakPerImage));

        AppLogger.info("批量嵌入开始: 文件数={}, 平均尺寸={}万像素, 最大堆={}MB, 每图估算={}MB, 动态并发数={}",
                total, avgPixels / 10000, maxMem / 1024 / 1024, estimatedPeakPerImage / 1024 / 1024, concurrency);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<FileResult>> futures = new ArrayList<>();

        for (File imageFile : imageFiles) {
            futures.add(pool.submit(() -> {
                FileResult fr = new FileResult();
                fr.setFileName(imageFile.getName());
                try {
                    BufferedImage image = fileService.readImage(imageFile);
                    BufferedImage watermarked = watermarkService.embedTextWatermark(
                            image, watermarkText, strength, password);
                    image.flush();
                    image = null;
                    File outFile = fileService.saveImageAs(watermarked, imageFile.getName(), "batch_embed", outDir);
                    watermarked.flush();
                    watermarked = null;
                    fr.setStatus("SUCCESS");
                    fr.setOutputPath(outFile.getAbsolutePath());
                    AppLogger.info("批量嵌入成功: {} -> {}", imageFile.getName(), outFile.getName());
                } catch (Throwable t) {
                    fr.setStatus("FAILED");
                    fr.setError(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
                    AppLogger.error("批量嵌入失败: {} - {}", imageFile.getName(), t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                return fr;
            }));
        }

        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AppLogger.error("批量处理被中断");
        }

        int successCount = 0;
        int failedCount = 0;
        List<FileResult> resultList = new ArrayList<>(total);
        for (Future<FileResult> future : futures) {
            try {
                FileResult fr = future.get();
                resultList.add(fr);
                if ("SUCCESS".equals(fr.getStatus())) {
                    successCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                FileResult fr = new FileResult();
                fr.setStatus("FAILED");
                fr.setError(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                resultList.add(fr);
                failedCount++;
            }
        }

        resultList.sort((a, b) -> {
            String nameA = a.getFileName() != null ? a.getFileName() : "";
            String nameB = b.getFileName() != null ? b.getFileName() : "";
            return nameA.compareToIgnoreCase(nameB);
        });

        result.setSuccess(successCount);
        result.setFailed(failedCount);
        result.getResults().addAll(resultList);
        result.setElapsedMs(System.currentTimeMillis() - startTime);

        AppLogger.info("批量嵌入完成: 总数={}, 成功={}, 失败={}, 耗时={}ms", total, result.getSuccess(), result.getFailed(), result.getElapsedMs());
        return result;
    }

    private File validateDirectory(String dirPath) {
        if (dirPath == null || dirPath.isBlank()) {
            throw new BusinessException(400, "目录路径不能为空");
        }
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new BusinessException(400, "目录不存在或不是有效目录: " + dirPath);
        }
        return dir;
    }

    private String resolveOutputDir(String outputDir, String subDir) {
        if (outputDir != null && !outputDir.isBlank()) return outputDir;
        return properties.getOutputDir() + File.separator + subDir;
    }

    private File[] listImageFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            return SUPPORTED_EXTENSIONS.contains(ext);
        });
        return files != null ? files : new File[0];
    }

    private int[] readImageDimension(File file) {
        String ext = file.getName().contains(".") ? file.getName().substring(file.getName().lastIndexOf('.') + 1).toLowerCase() : "";
        Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix(ext);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            try (FileImageInputStream fis = new FileImageInputStream(file)) {
                reader.setInput(fis);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } catch (Exception ignored) {
            } finally {
                reader.dispose();
            }
        }
        return null;
    }
}
