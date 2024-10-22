import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 符合Java规范数据集的特征提取
 */
public class CodeFeatureExtractor {

    public static void main(String[] args) throws IOException, URISyntaxException {
        //读取resources文件夹下的数据集代码根目录
        ClassLoader classLoader = CodeFeatureExtractor.class.getClassLoader();
        URL resource = classLoader.getResource("CoESTData/iTrust/iTrust-code/iTrust/src");
        if (resource == null) {
            throw new IllegalArgumentException("未找到资源文件路径！");
        }
        Path projectPath = Paths.get(resource.toURI());

        //存储特征总集和文件名总集
        //4类特征：类名(CN)、方法名(MN)、变量名(VN)、注释(CMT)
        List<String> allClassNames = new ArrayList<>();
        List<String> allMethodNames = new ArrayList<>();
        List<String> allVariableNames = new ArrayList<>();
        List<String> allComments = new ArrayList<>();
        List<String> allFileNames = new ArrayList<>();

        AtomicInteger cnt = new AtomicInteger(1);
        Files.walk(projectPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(filePath -> {
                    try {
                        System.out.println("处理文件：" + filePath + " 当前行数：" + cnt);
                        cnt.getAndIncrement();

                        //依次遍历.java文件，提取各特征及文件名，每个文件的特征分别存储于一行中，不同文件的特征按行隔开
                        FileFeatures features = parseJavaFile(filePath);
                        allClassNames.add(String.join(" ", features.classNames));
                        allMethodNames.add(String.join(" ", features.methodNames));
                        allVariableNames.add(String.join(" ", features.variableNames));
                        allComments.add(String.join(" ", features.comments));
                        allFileNames.add(String.valueOf(filePath.getFileName()));
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                });

        //保存各特征和文件名到txt文件中
        saveToTxt("CN.txt", allClassNames);
        saveToTxt("MN.txt", allMethodNames);
        saveToTxt("VN.txt", allVariableNames);
        saveToTxt("CMT.txt", allComments);
        saveToTxt("CMT_Name.txt", allFileNames);
    }

    /**
     * 每次文件迭代的各特征集合(类名、方法名、变量名、注释)
     */
    private static class FileFeatures {
        List<String> classNames = new ArrayList<>();
        List<String> methodNames = new ArrayList<>();
        List<String> variableNames = new ArrayList<>();
        List<String> comments = new ArrayList<>();
    }

    /**
     * 文件解析，提取特征
     * @param filePath 文件路径
     * @return FileFeatures 文件特征集合
     * @throws FileNotFoundException 文件不存在
     */
    private static FileFeatures parseJavaFile(Path filePath) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath.toString()));
        FileFeatures features = new FileFeatures();

        cu.accept(new VoidVisitorAdapter<Void>() {
            //提取类名(普通类和接口，不包含枚举)
            @Override
            public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
                features.classNames.add(cid.getNameAsString());
                super.visit(cid, arg);

                // 提取内部类类名
                cid.getMembers().forEach(member -> {
                    if (member instanceof ClassOrInterfaceDeclaration) {
                        features.classNames.add(((ClassOrInterfaceDeclaration) member).getNameAsString());
                    }
                });
            }

            //提取方法名(不包含构造器)
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                features.methodNames.add(md.getNameAsString());
                super.visit(md, arg);
            }

            //提取变量名
            @Override
            public void visit(VariableDeclarator vd, Void arg) {
                features.variableNames.add(vd.getNameAsString());
                super.visit(vd, arg);
            }

            //提取构造器方法名
            @Override
            public void visit(ConstructorDeclaration cd, Void arg) {
                features.methodNames.add(cd.getNameAsString());
                super.visit(cd, arg);
            }

            //提取枚举类类名
            @Override
            public void visit(EnumDeclaration ed, Void arg) {
                features.classNames.add(ed.getNameAsString());
                //提取枚举常量
                //ed.getEntries().forEach(entry -> features.variableNames.add(entry.getNameAsString()));
                super.visit(ed, arg);
            }
        }, null);

        // 提取所有注释
        cu.getAllContainedComments().forEach(comment ->
                features.comments.add(cleanComment(comment.getContent()))
        );

        return features;
    }

    /**
     * 去除多余的换行和空格
     * @param comment 待处理的注释
     * @return 处理后的注释
     */
    private static String cleanComment(String comment) {
        return comment.replaceAll("[\\r\\n]+", " ").trim();
    }

    /**
     * 保存特征数据到指定文件中，每行对应一个源文件的所有该特征
     * @param fileName 要保存的文件路径
     * @param data 特征数据集合
     */
    private static void saveToTxt(String fileName, List<String> data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (String line : data) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
