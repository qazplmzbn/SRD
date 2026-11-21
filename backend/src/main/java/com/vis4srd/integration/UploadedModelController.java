package com.vis4srd.integration;

import com.vis4srd.common.exception.BusinessException;
import com.vis4srd.common.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model-analysis")
@RequiredArgsConstructor
public class UploadedModelController {
 private final PythonInferenceClient client;
 public record Input(
  @NotBlank @Size(max=100) String userHash,
  @NotNull @Pattern(regexp="reddit|weibo|bigdata|sigir") String dataSource,
  @NotEmpty @Size(max=50) List<@NotNull @Size(min=768,max=768) List<@NotNull Double>> bertEmbeddings,
  @NotNull @Size(max=50) List<@NotNull @Size(max=100) List<@NotNull @Size(max=32) String>> emojiSequences,
  @NotNull @Size(max=50) List<@NotNull @Size(max=10000) String> posts,
  @AssertTrue boolean consent) {}
 @PostMapping("/emocc")
 public ApiResponse<Map<String,Object>> analyze(@Valid @RequestBody Input input) {
  int count=input.bertEmbeddings().size();
  if(input.emojiSequences().size()!=count || input.posts().size()!=count || input.bertEmbeddings().stream().flatMap(List::stream).anyMatch(v->!Double.isFinite(v)))
   throw new BusinessException(HttpStatus.BAD_REQUEST,"特征、表情和贴文必须逐条对应，数值必须有限");
  var result=client.inferUploadedEmocc(Map.of("userHash",input.userHash(),"dataSource",input.dataSource(),"bertEmbeddings",input.bertEmbeddings(),"emojiSequences",input.emojiSequences(),"posts",input.posts()));
  return ApiResponse.ok(result.orElseThrow(()->new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"Emocc 推理失败：请检查推理服务、对应权重和特征格式。未生成替代结果。")));
 }
}
