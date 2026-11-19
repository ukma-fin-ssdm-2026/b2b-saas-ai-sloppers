package ua.edu.ukma.community.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.error.FieldViolation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@Service
public class RegistrationService {

    private static final int CODE_BOUND = 1_000_000;

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final CodeSender codeSender;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(UserRepository users, PasswordEncoder passwords, CodeSender codeSender,
                               AuthProperties properties, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.codeSender = codeSender;
        this.properties = properties;
        this.clock = clock;
    }


    public synchronized User register(String rawEmail, String password) {
        String email = Emails.normalize(rawEmail);
        if (!Emails.hasDomain(email, properties.allowedDomain())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Запит містить невалідні поля",
                    List.of(new FieldViolation("email",
                            "Реєстрація лише з університетською поштою @" + properties.allowedDomain(), rawEmail)));
        }
        if (users.findByEmail(email).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                    "Акаунт з цією поштою вже існує. Увійдіть або запросіть код підтвердження повторно");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        String code = newCode();
        User user = new User(UUID.randomUUID(), email, passwords.encode(password), UserStatus.PENDING,
                properties.isAdminEmail(email), verification(code, now), now, now);
        codeSender.send(email, code);
        return users.save(user);
    }

    public synchronized void verify(String rawEmail, String code) {
        String email = Emails.normalize(rawEmail);
        User user = users.findByEmail(email).orElseThrow(RegistrationService::invalidCode);
        if (user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_ALREADY_VERIFIED, "Пошту вже підтверджено, можна входити");
        }
        if (user.status() == UserStatus.BLOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED, "Акаунт заблоковано");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        checkCode(user, code, now);
        users.save(user.activated(now));
    }

    public synchronized void resendCode(String rawEmail) {
        String email = Emails.normalize(rawEmail);
        User user = users.findByEmail(email).orElse(null);
        if (user == null || user.status() != UserStatus.PENDING) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        requireCooldownPassed(user, now);
        String code = newCode();
        codeSender.send(email, code);
        users.save(user.withVerification(verification(code, now), now));
    }

    /**
     * Скидання пароля, крок 1 (FR-065): надсилає код на пошту. Для невідомої чи заблокованої пошти тихо
     * нічого не робить, щоб відповідь не розкривала, які пошти зареєстровані. Код займає те саме місце,
     * що й код підтвердження, тож у непідтвердженого акаунта він замінює попередній.
     */
    public synchronized void requestPasswordReset(String rawEmail) {
        String email = Emails.normalize(rawEmail);
        User user = users.findByEmail(email).orElse(null);
        if (user == null || user.status() == UserStatus.BLOCKED) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        requireCooldownPassed(user, now);
        String code = newCode();
        codeSender.sendPasswordReset(email, code);
        users.save(user.withVerification(verification(code, now), now));
    }

    /** Скидання пароля, крок 2 (FR-065): правильний код із листа замінює пароль. */
    public synchronized void resetPassword(String rawEmail, String code, String newPassword) {
        String email = Emails.normalize(rawEmail);
        User user = users.findByEmail(email).orElseThrow(RegistrationService::invalidCode);
        if (user.status() == UserStatus.BLOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED, "Акаунт заблоковано");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        checkCode(user, code, now);
        users.save(user.withNewPassword(passwords.encode(newPassword), now));
    }

    /** Спільна перевірка коду для підтвердження пошти й скидання пароля: строк, спроби, збіг (FR-003). */
    private void checkCode(User user, String code, OffsetDateTime now) {
        VerificationCode current = user.verification();
        if (current == null || !current.usable(now)) {
            throw new ApiException(ErrorCode.VERIFICATION_CODE_EXPIRED,
                    "Код прострочений або вичерпано спроби. Запросіть новий код");
        }
        if (code == null || !passwords.matches(code.trim(), current.codeHash())) {
            VerificationCode failed = current.withFailedAttempt();
            users.save(user.withVerification(failed, now));
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE, "Неправильний код",
                    List.of(new FieldViolation("code", "Залишилось спроб: " + failed.attemptsLeft(), null)));
        }
    }

    private void requireCooldownPassed(User user, OffsetDateTime now) {
        VerificationCode current = user.verification();
        if (current != null && now.isBefore(current.sentAt().plus(properties.resendCooldown()))) {
            long wait = Duration.between(now, current.sentAt().plus(properties.resendCooldown())).toSeconds() + 1;
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS,
                    "Новий код можна запросити через " + wait + " с");
        }
    }

    private VerificationCode verification(String code, OffsetDateTime now) {
        return new VerificationCode(passwords.encode(code), now.plus(properties.codeTtl()),
                properties.codeAttempts(), now);
    }

    private String newCode() {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }

    private static ApiException invalidCode() {
        return new ApiException(ErrorCode.INVALID_VERIFICATION_CODE, "Неправильний код");
    }
}
