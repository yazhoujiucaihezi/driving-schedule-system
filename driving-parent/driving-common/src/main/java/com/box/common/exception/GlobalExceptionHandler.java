package com.box.common.exception;

import com.box.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一拦截 Controller 层抛出的各类异常，转换为标准 {@link Result} 结构返回，
 * 保证前端始终拿到 { code, message, data } 格式，而不是默认的错误页或裸堆栈。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 — 由 BusinessException 携带的 code / message 原样返回。
     * 属于预期内的业务校验失败（排班已满、重复预约、车辆维修中等），只记 warn。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @PreAuthorize 权限不足异常 — 返回 403 JSON。
     * <p>
     * Spring Security 方法级权限校验失败时抛出 AccessDeniedException，
     * 需显式捕获并返回统一 Result 格式，否则会被兜底 ExceptionHandler 包装成 500。
     * </p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return Result.error(HttpStatus.FORBIDDEN.value(), "没有权限访问该资源");
    }

    /**
     * 手动抛出的状态码异常（如网关 / 拦截器中 new ResponseStatusException）。
     * 直接映射其自带 status 与 reason。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Result<Void> handleResponseStatusException(ResponseStatusException e) {
        log.warn("状态码异常：status={}, reason={}", e.getStatusCode(), e.getReason());
        return Result.error(e.getStatusCode().value(),
                e.getReason() != null ? e.getReason() : "请求处理失败");
    }

    /**
     * 请求方法不支持（如 GET 接口用 POST 调用）→ 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：{}", e.getMethod());
        return Result.error(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方式不支持，请检查后重试");
    }

    /**
     * @Valid 校验失败（作用于 @RequestBody）→ 返回拼接后的字段错误信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败：{}", msg);
        return Result.error(HttpStatus.BAD_REQUEST.value(), msg.isEmpty() ? "参数校验失败" : msg);
    }

    /**
     * 表单 / query 参数绑定校验失败（@Valid 作用于非 @RequestBody 对象）→ 400。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败：{}", msg);
        return Result.error(HttpStatus.BAD_REQUEST.value(), msg.isEmpty() ? "参数校验失败" : msg);
    }

    /**
     * 缺少必填请求参数（@RequestParam 未传）→ 400。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数：{}", e.getParameterName());
        return Result.error(HttpStatus.BAD_REQUEST.value(), "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 参数类型不匹配（如 id 传了非数字）→ 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误：name={}, value={}", e.getName(), e.getValue());
        return Result.error(HttpStatus.BAD_REQUEST.value(), "参数类型错误：" + e.getName());
    }

    /**
     * 参数校验失败的兜底（手动抛出，如 Assert、Preconditions）→ 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数校验失败：{}", e.getMessage());
        return Result.error(HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    /**
     * 请求体无法解析（空 body、JSON 格式错误、中文乱码等）→ 400。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.error(HttpStatus.BAD_REQUEST.value(), "请求参数格式错误，请检查后重试");
    }

    /**
     * 兜底异常处理 — 未被上面捕获的所有异常统一返回 500，并打印完整堆栈便于排查。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误，请稍后重试");
    }

    /** 将字段错误格式化为 “字段: 提示”，方便前端直接展示。 */
    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
