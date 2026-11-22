package com.vis4srd.research;

import com.vis4srd.common.exception.BusinessException;
import org.apache.commons.csv.CSVFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.util.*;

final class ResearchCsv {
    private static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }

    private static String decode(byte[] bytes) {
        var buffer = ByteBuffer.wrap(bytes);
        try {
            if (bytes.length >= 2 && (buffer.getShort(0) == (short)0xfffe || buffer.getShort(0) == (short)0xfeff))
                return StandardCharsets.UTF_16.newDecoder().decode(buffer).toString();
            try { return StandardCharsets.UTF_8.newDecoder().decode(buffer).toString().replaceFirst("^\uFEFF", ""); }
            catch (CharacterCodingException utf8) { return Charset.forName("GB18030").newDecoder().decode(buffer.rewind()).toString(); }
        } catch (CharacterCodingException e) { throw invalid("无法识别文件编码，请在 Excel 中另存为 CSV UTF-8（逗号分隔）"); }
    }

    record Grid(List<String> columns,List<List<String>> rows) {}
    static Grid table(MultipartFile file) {
        if (!Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT).endsWith(".csv")) throw invalid("请选择 CSV 文件");
        if (file.getSize()>10L*1024*1024) throw invalid("单个文件超过 10 MB");
        try(var parser=CSVFormat.DEFAULT.parse(new StringReader(decode(file.getBytes())))) {
            var iter=parser.iterator();if(!iter.hasNext())throw invalid("文件为空");
            var columns=new ArrayList<String>();iter.next().forEach(columns::add);
            var rows=new ArrayList<List<String>>();
            while(iter.hasNext()){var row=new ArrayList<String>();iter.next().forEach(row::add);
                while(columns.size()<row.size())columns.add("");rows.add(row);}
            return new Grid(columns,rows);
        }catch(IOException|UncheckedIOException|IllegalStateException ex){throw invalid("CSV 无法解析，请检查引号格式");}
    }
    static List<ResearchVersionController.Sample> read(MultipartFile file, String text, String label, String author, String split) {
        if (!Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT).endsWith(".csv")) throw invalid("请选择 CSV 文件");
        if (file.getSize() > 1048576) throw invalid("文件超过 1 MB");
        var fields = List.of(text.strip(), label.strip(), author.strip(), split.strip());
        if (fields.get(0).isEmpty() || fields.get(1).isEmpty()) throw invalid("请填写贴文列和标签列");
        try {
            var csv = decode(file.getBytes());
            try (var parser = CSVFormat.DEFAULT.parse(new StringReader(csv))) {
                var records = parser.iterator();
                if (!records.hasNext()) throw invalid("CSV 缺少表头");
                Map<String,Integer> header = new HashMap<>();
                for (String cell : records.next()) {
                    if (cell.isBlank() || header.put(cell.strip(), header.size()) != null) throw invalid("表头不能为空或重复");
                }
                var used = new HashSet<String>();
                for (String field : fields) if (!field.isEmpty()) {
                    if (!header.containsKey(field)) throw invalid("文件中没有列：" + field);
                    if (!used.add(field)) throw invalid("不同字段不能对应同一列");
                }
                var samples = new ArrayList<ResearchVersionController.Sample>();
                while (records.hasNext()) {
                    var row = records.next(); int n = samples.size()+1;
                    if (n > 100) throw invalid("每次最多导入 100 条");
                    if (row.size()!=header.size()) throw invalid("第 " + n + " 条数据与表头列数不同");
                    String post=row.get(header.get(fields.get(0))).strip(), tag=row.get(header.get(fields.get(1))).strip();
                    String subject=fields.get(2).isEmpty()?"row-"+n:row.get(header.get(fields.get(2))).strip();
                    String group=fields.get(3).isEmpty()?"TEST":row.get(header.get(fields.get(3))).strip().toUpperCase(Locale.ROOT);
                    if (post.isEmpty() || post.length()>8000 || !List.of("0","1").contains(tag) || subject.isEmpty() || subject.length()>100 || !List.of("TRAIN","TEST").contains(group)) throw invalid("第 " + n + " 条数据无效，请检查贴文、0/1 标签、作者和 TRAIN/TEST 划分");
                    samples.add(new ResearchVersionController.Sample(subject,post,Integer.valueOf(tag),group));
                }
                if (samples.isEmpty()) throw invalid("CSV 没有数据行");
                return samples;
            } catch (UncheckedIOException | IllegalStateException e) {
                throw invalid("CSV 记录格式错误：请检查含逗号或换行的字段是否用双引号包围，以及字段内部的双引号是否写成两个双引号");
            }
        } catch (IOException e) { throw invalid("文件读取失败，请重新选择 CSV 文件"); }
    }
}