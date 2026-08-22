package interview.guide.modules.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 320, message = "邮箱不能超过 320 个字符")
    String email,
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度必须为 8 到 72 个字符")
    String password
) {}
