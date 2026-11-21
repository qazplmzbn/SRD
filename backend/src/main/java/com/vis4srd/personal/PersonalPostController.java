package com.vis4srd.personal;

import com.vis4srd.common.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/me/posts")
@RequiredArgsConstructor
public class PersonalPostController {
    private final PersonalPostService service;

    public record PostInput(
            @NotBlank(message = "贴文内容不能为空") @Size(max = 10000, message = "每条贴文最多10000字") String content,
            @NotBlank(message = "请填写来源") @Size(max = 40, message = "来源最多40字") String source,
            Instant publishedAt) {}

    public record ImportRequest(
            @AssertTrue(message = "请确认将所选贴文保存到个人账号") boolean consent,
            @NotEmpty(message = "请选择贴文") @Size(max = 100, message = "每次最多导入100条")
            List<@NotNull @Valid PostInput> posts) {}

    @PostMapping("/import")
    public ApiResponse<PersonalPostService.ImportResult> importPosts(@Valid @RequestBody ImportRequest request) {
        return ApiResponse.ok(service.importPosts(request.posts()));
    }

    @GetMapping
    public ApiResponse<PersonalPostService.PostPage> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(page, pageSize));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(null, "已删除此条个人贴文");
    }
}
