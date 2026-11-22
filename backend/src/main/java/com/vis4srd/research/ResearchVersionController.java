package com.vis4srd.research;
import com.vis4srd.common.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/research/versions") @RequiredArgsConstructor
public class ResearchVersionController {
 private final ResearchVersionService service;
 @PostMapping(value="/table-preview",consumes="multipart/form-data")
 public ApiResponse<?> previewTable(@RequestParam org.springframework.web.multipart.MultipartFile file){
   service.actor();var table=ResearchCsv.table(file);return ApiResponse.ok(Map.of("columns",table.columns(),"rows",table.rows().stream().limit(5).toList(),"total",table.rows().size()));
 }
 public record TableMeta(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=500) String source,@NotBlank @Size(max=1000) String usageScope,@AssertTrue boolean consent,@NotNull List<@NotNull String> columns,Integer labelColumn,List<Integer> postColumns){}
 @PostMapping(value="/table",consumes="multipart/form-data")
 public ApiResponse<?> createTable(@RequestParam org.springframework.web.multipart.MultipartFile file,@Valid @RequestPart TableMeta meta){
   service.actor();var table=ResearchCsv.table(file);if(meta.columns().size()!=table.columns().size())throw new com.vis4srd.common.exception.BusinessException("字段数量与文件不一致");
   return ApiResponse.ok(Map.of("id",service.createTable(meta,table)));
 }
 @PostMapping(value="/csv-preview",consumes="multipart/form-data")
 public ApiResponse<?> preview(@RequestParam org.springframework.web.multipart.MultipartFile file,@RequestParam String text,@RequestParam String label,@RequestParam(defaultValue="") String subjectId,@RequestParam(defaultValue="") String split){
   service.actor();return ApiResponse.ok(ResearchCsv.read(file,text,label,subjectId,split));
 }
 public record Sample(@NotBlank @Size(max=100) String subjectId,@NotBlank @Size(max=8000) String text,
                      @NotNull @Min(0) @Max(1) Integer label,@NotNull @Pattern(regexp="TRAIN|TEST") String split) {}
 public record Create(@NotBlank @Size(max=100) String name,@Size(max=40) String versionName,
                      @NotBlank @Size(max=500) String source,@NotBlank @Size(max=1000) String usageScope,
                      @AssertTrue(message="请确认有权导入研究数据") boolean consent,
                      @NotEmpty @Size(max=100) List<@NotNull @Valid Sample> samples) {}
 public record Grant(@Positive long userId, boolean revoke) {}
 @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping public ApiResponse<?> list(@RequestParam(defaultValue="1") int page){return ApiResponse.ok(service.list(page));}
 @PostMapping public ApiResponse<?> create(@Valid @RequestBody Create input){return ApiResponse.ok(Map.of("id",service.create(input)));}
 @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable String id){return ApiResponse.ok(service.detail(id));}
 @GetMapping("/{id}/grants") public ApiResponse<?> grants(@PathVariable String id){return ApiResponse.ok(service.grants(id));}
 @PostMapping("/{id}/grants") public ApiResponse<?> grant(@PathVariable String id,@Valid @RequestBody Grant grant){service.grant(id,grant);return ApiResponse.ok(null);}
}
