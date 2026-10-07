package com.gametrust.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String username, String otp) {
        log.info("\n========================================================================"
                + "\n[GAMETRUST OTP CODE]: To: {} (User: {})"
                + "\n>>> YOUR VERIFICATION CODE IS: {} <<< (Valid for 5 minutes)"
                + "\n========================================================================", toEmail, username, otp);

        if (mailSender == null || fromEmail == null || fromEmail.isBlank()) {
            log.info("[EmailService] SMTP credentials not set. OTP printed to console log above.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "GameTrust Esports Security");
            helper.setTo(toEmail);
            helper.setSubject("[GameTrust] Mã xác nhận đăng ký tài khoản: " + otp);

            String htmlContent = """
                    <div style="background-color: #07090E; color: #FFFFFF; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; padding: 30px; border: 1px solid #00F0FF; border-radius: 4px;">
                        <div style="text-align: center; margin-bottom: 25px;">
                            <h1 style="color: #00F0FF; font-size: 26px; letter-spacing: 2px; margin: 0; text-transform: uppercase;">GAMETRUST ESPORTS</h1>
                            <p style="color: #8E9BAE; font-size: 12px; margin: 5px 0 0; letter-spacing: 1px;">PLAYER IDENTITY & VERIFICATION PROTOCOL</p>
                        </div>
                        <div style="background-color: #0D121B; border-left: 4px solid #FF007F; padding: 20px; margin-bottom: 25px;">
                            <p style="margin: 0; font-size: 15px; color: #E1E7EF;">Xin chào <strong style="color: #00F0FF;">%s</strong>,</p>
                            <p style="margin: 10px 0 0; font-size: 14px; color: #8E9BAE; line-height: 1.5;">
                                Bạn vừa yêu cầu đăng ký tài khoản trên nền tảng GameTrust. Vui lòng sử dụng mã xác thực bảo mật 6 chữ số dưới đây để kích hoạt tài khoản:
                            </p>
                        </div>
                        <div style="text-align: center; margin: 30px 0;">
                            <div style="display: inline-block; background-color: #101724; border: 2px solid #00F0FF; padding: 15px 35px; border-radius: 4px; box-shadow: 0 0 15px rgba(0, 240, 255, 0.3);">
                                <span style="font-size: 32px; font-weight: 900; letter-spacing: 8px; color: #00F0FF; font-family: monospace;">%s</span>
                            </div>
                        </div>
                        <p style="font-size: 12px; color: #FFD700; text-align: center; margin-bottom: 30px;">
                            ⚠ Mã xác thực có hiệu lực trong vòng <strong>5 phút</strong>. Tuyệt đối không chia sẻ mã này cho bất kỳ ai.
                        </p>
                        <div style="border-top: 1px solid #1E293B; padding-top: 15px; text-align: center; font-size: 11px; color: #64748B;">
                            Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.<br/>
                            &copy; 2026 GameTrust Platform. All rights reserved.
                        </div>
                    </div>
                    """.formatted(username, otp);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("[EmailService] Real OTP email successfully dispatched to {}", toEmail);
        } catch (Exception e) {
            log.warn("[EmailService] Could not send via SMTP (check credentials/app password): {}. Fallback OTP is still valid in logs.", e.getMessage());
        }
    }
}
