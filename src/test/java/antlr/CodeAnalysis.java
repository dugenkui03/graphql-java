package antlr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Description
 * @Date 2020/7/30
 *
 * @Author dugenkui
 **/
public class CodeAnalysis {

    private static AtomicLong count = new AtomicLong(0);

    public static long countCodeLine(String rootDir, List<String> postfix) throws IOException {
        File file = new File(rootDir);
        if (file.isDirectory()) {
            int totalCount = 0;
            for (File listFile : file.listFiles()) {
                totalCount += countCodeLine(listFile.getAbsolutePath(), postfix);
            }
            return totalCount;
        } else {
            String absolutePath = file.getAbsolutePath();
            if (postfix.stream().filter(ele -> absolutePath.endsWith(ele)).count() <= 0) {
                return 0;
            }
            count.incrementAndGet();
            return Files.lines(Paths.get(absolutePath)).count();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println(countCodeLine("/Users/dugenkui/github/graphql-java-calculate", Arrays.asList(".java")));
        System.out.println(count.get());
    }

}
