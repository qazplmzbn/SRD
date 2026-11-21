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
 public record Sample(@NotBlank @Size(max=100) String subjectId,@NotBlank @Size(max=8000) String text,
                      @NotNull @Min(0) @Max(1) Integer label,@NotNull @Pattern(regexp="TRAIN|TEST") String split) {}
 public record Create(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=40) String versionName,
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
