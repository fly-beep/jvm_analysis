import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GCTuningDemo {
    private static final int OBJECT_SIZE = 1024; // 1KB
    private static final int LIST_SIZE = 10000;  // 10K个对象 
    private static final int ITERATIONS = 1000;  // 迭代次数  

    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < ITERATIONS; i++) {
            // 进度条显示
            printProgress("Iteration", i, ITERATIONS);

            for (int j = 0; j < LIST_SIZE; j++) {
                list.add(new byte[OBJECT_SIZE]);
            }
            // 模拟随机删除
            for (int j = 0; j < LIST_SIZE / 2; j++) {
                list.remove(random.nextInt(list.size()));
            }
        }
        System.out.println("\nCompleted all iterations.");
    }

    private static void printProgress(String task, int current, int total) {
        int progress = (int) ((current / (double) total) * 100);
        StringBuilder progressBar = new StringBuilder("[");
        int progressBarLength = 50; // 进度条长度
        int filledLength = (int) (progressBarLength * progress / 100.0);

        for (int i = 0; i < progressBarLength; i++) {
            if (i < filledLength) {
                progressBar.append("=");
            } else {
                progressBar.append(" ");
            }
        }
        progressBar.append("]");

        System.out.printf("\r%s: %s %d%%", task, progressBar.toString(), progress);
    }
}
