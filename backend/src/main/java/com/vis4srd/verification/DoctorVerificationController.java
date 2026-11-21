package com.vis4srd.verification;

import com.vis4srd.common.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DoctorVerificationController {
    private final DoctorVerificationService service;

    @ModelAttribute
    public void noCache(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    public record ApplicationInput(
            @NotBlank @Size(max=80) String realName,
            @NotBlank @Size(max=200) String institution,
            @NotBlank @Size(max=100) String qualification,
            @NotBlank @Size(max=120) String credentialNumber,
            @NotNull @Size(max=1000) String notes,
            @AssertTrue(message="请确认材料真实并同意用于医生认证审核") boolean consent) {
        @Override public String toString() { return "ApplicationInput[redacted]"; }
    }
    public record ReviewInput(@Pattern(regexp="APPROVE|REJECT|REVOKE") @NotNull String decision,
                              @NotBlank(message="请填写核查结论或处理原因") @Size(max=1000) String reason) {
        @Override public String toString() { return "ReviewInput[redacted]"; }
    }

    @GetMapping("/me/doctor-applications")
    public ApiResponse<?> mine() { return ApiResponse.ok(service.mine()); }

    @PostMapping(value="/me/doctor-applications", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> submit(@Valid @RequestPart("application") ApplicationInput input,
                                 @RequestPart("proof") MultipartFile proof) {
        return ApiResponse.ok(Map.of("id", service.submit(input, proof)));
    }

    @GetMapping("/admin/doctor-applications")
    public ApiResponse<?> list(@RequestParam(defaultValue="1") int page,
                              @RequestParam(defaultValue="ALL") String status) {
        return ApiResponse.ok(service.list(page, status));
    }

    @PostMapping("/admin/doctor-applications/{id}/review")
    public ApiResponse<?> review(@PathVariable String id, @Valid @RequestBody ReviewInput input) {
        service.review(id, input); return ApiResponse.ok(null, "审核结果已保存");
    }

    @GetMapping("/doctor-applications/{id}/proof")
    public ResponseEntity<byte[]> proof(@PathVariable String id) {
        var proof = service.proof(id);
        String extension = proof.type().equals("application/pdf") ? "pdf" : proof.type().equals("image/png") ? "png" : "jpg";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(proof.type()))
                .header("Content-Disposition", "attachment; filename=\"qualification." + extension + "\"")
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff").body(proof.content());
    }
}
