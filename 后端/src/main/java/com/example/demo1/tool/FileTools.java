package com.example.demo1.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Collectors;
@Component
public class FileTools {

    //定义安全边界
    //添加用户的主目录
    private static final Path HOME= Paths.get(System.getProperty("user.home"));
    //文件大小上限
    private static final int MAX_FILE_SIZE=100*1024;
    //文件字符上限
    private static final int MAX_READ_CHARS=5000;
    //ai文件上限
    private static final int MAX_SEARCH_RESULTS=30;
    //添加一个安全防护,防止信息透露给ai
    private Path resolveSafePath(String input){
        //将用户输入放入home
        Path resolved=HOME.resolve(input.replace("\\","/")).normalize();
        //安全检查
        if(!resolved.startsWith((HOME))){
            throw new SecurityException(("路径超出允许范围"+input));
        }
        return resolved;
    }
    //字节转化表达式子
    private String formatSize(long bytes){
        if(bytes<1024){
            return bytes+"B";
        }else if(bytes<1024*1024){
            return String.format("%.1f KB",bytes/1024.0);
        }else{
            return String.format("%.1f MB",bytes/1024.0/1024.0);
        }
    }
    //判断文件类型,判断产生是不是乱码文件
    private boolean isTextFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes((path));
        int length=Math.min(bytes.length,512);
        int nonPrintable=0;
        for(int i=0;i<length;i++){
            int b=bytes[i]&0xFF;
            if(b<0x09||(b>0x0D&&b<0x20)||b==0x7F){
                nonPrintable++;
            }
        }
        return (double) nonPrintable/length<0.3;
    }
    //索引文件目录
    @Tool(description = "列出指定目录下的文件和子文件目录,参数path是相对于用户主目录的路径")
    public String listDirectory(
            @ToolParam(description = "要列出目录,相对于用户主目录")String path
    ) throws IOException {
        //安全解析
        Path dir = resolveSafePath(path.isEmpty() ? "" : path);
        //验证路径是否真实存在
        if (!Files.isDirectory(dir)) {
            return "路径不是目录" + dir;
        }
        String listing = Files.list(dir).sorted().map((Path p)->{
            //判断是目录还是文件
            String prefix = Files.isDirectory(p) ? "[DIR]" : "[FILE]";
            String size = Files.isDirectory(p) ? "" : formatSize(p.toFile().length());
            return prefix + p.getFileName() + size;
        }).collect(Collectors.joining("\n"));
        return listing.isEmpty()?"目录为空":dir+"\n"+listing;
    }
    //读取目录
    @Tool(description = "读取文件内容,但只能读文本文件,超过5000字自动截断,参数path是相对于用户的主目录")
    public String readFile(
            @ToolParam(description = "要读的文件路径,相对于用户的主目录")String path
    ) throws IOException {
        //添加防护
        Path file=resolveSafePath((path));
        if(!Files.isRegularFile(file)){
            return "文件不存在"+file;
        }
        if(Files.size(file)>MAX_FILE_SIZE){
            return "文件过大"+file;
        }
        if(!isTextFile((file))){
            return "无法读取二进制文件";
        }
        String content=Files.readString(file, StandardCharsets.UTF_8);
        if(content.length()>MAX_READ_CHARS){
            content=content.substring(0,MAX_READ_CHARS)+"....(截断到前5000个字)";

        }
        return "文件内容为;("+file.getFileName()+"):"+content;
    }
    //搜索目录文件
    @Tool(description = "再指定目录下递归搜索文件名包含关键字的文件,最多返回30个结果")
    public String searchFiles(
            @ToolParam(description ="搜索的起始目录,相对于用户主目录")String directory,
            @ToolParam(description="文件名匹配的关键字")String keyword
    ) throws IOException {
        Path dir=resolveSafePath(directory.isEmpty()?"":directory);
        if(!Files.isDirectory(dir)){
            return "路径不是目录"+dir;
        }
        StringBuilder results=new StringBuilder();
        int[] count={0};
        Files.walkFileTree(dir,new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName =file.getFileName().toString().toLowerCase();
                if(fileName.contains(keyword.toLowerCase())){
                    if(count[0]<MAX_SEARCH_RESULTS){
                        //显示相对路径
                        results.append(dir.relativize(file))
                                .append("\n")
                                .append(fileName);
                        count[0]++;

                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return results.isEmpty()?"未找到文件":dir+"搜索:\n"+keyword+"找到:\n"+count[0]+"个"+results;
    }

}
