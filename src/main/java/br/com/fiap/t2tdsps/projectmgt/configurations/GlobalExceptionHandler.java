package br.com.fiap.t2tdsps.projectmgt.configurations;

import br.com.fiap.t2tdsps.projectmgt.dto.ErrorCustomResponse;
import br.com.fiap.t2tdsps.projectmgt.dto.ErrorFieldCustom;
import br.com.fiap.t2tdsps.projectmgt.dto.ValidationErrorResponse;
import br.com.fiap.t2tdsps.projectmgt.exceptions.EmailAlreadyRegisteredException;
import br.com.fiap.t2tdsps.projectmgt.exceptions.InvalidStatusTransitionException;
import br.com.fiap.t2tdsps.projectmgt.exceptions.MilestoneAlreadyCompletedException;
import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorCustomResponse> handleException(ResourceNotFoundException ex){
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorCustomResponse> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MilestoneAlreadyCompletedException.class)
    public ResponseEntity<ErrorCustomResponse> handleMilestoneAlreadyCompleted(MilestoneAlreadyCompletedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorCustomResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 401: credenciais erradas no login (BadCredentialsException é uma AuthenticationException).
    // Mensagem GENÉRICA de propósito: não revela se o erro foi no email ou na senha.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorCustomResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
    }

    // 403: usuário autenticado, mas sem permissão. Vem de @PreAuthorize (AuthorizationDeniedException) ou da regra
    // de posse no service. SEM este handler, o catch-all Exception.class abaixo transformaria tudo em 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorCustomResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Você não tem permissão para executar esta ação");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorCustomResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou malformado");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorCustomResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro '" + ex.getName() + "' inválido: " + ex.getValue());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorCustomResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Exerc resolvido: @Valid num @RequestBody que falha lança MethodArgumentNotValidException, com TODAS as
    // violações dentro de ex.getBindingResult() - não só a primeira. Separamos os dois tipos que o BindingResult
    // guarda: getFieldErrors() (violação de UM campo, ex: @NotBlank em "name") e getGlobalErrors() (violação da
    // classe inteira, ex: a constraint customizada @ValidDateRange, que compara startDate com endDate).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleArgumentNotValidException(MethodArgumentNotValidException ex){

        // 1. cria uma lista de ErrorFieldCustom
        // 2. popular essa lista com os erros encontrados
        // 3. retorna uma ResponseEntity com o status 400 e a lista de erros

        List<ErrorFieldCustom> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorFieldCustom(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        List<ErrorFieldCustom> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(globalError -> new ErrorFieldCustom(globalError.getObjectName(), globalError.getDefaultMessage()))
                .toList();

        List<ErrorFieldCustom> errors = Stream.concat(fieldErrors.stream(), globalErrors.stream()).toList();

        ValidationErrorResponse body = new ValidationErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                errors
        );
        return ResponseEntity.badRequest().body(body);
    }

    // Validação de @PathVariable/@RequestParam (ex: @Positive no id) usa @Validated na classe do controller,
    // não @Valid - a violação chega aqui, não em MethodArgumentNotValidException, porque não existe um
    // "objeto" pra montar um BindingResult, só um valor solto.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorCustomResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    // Desafio resolvido: sem esse handler, qualquer erro não previsto (bug, NullPointerException etc.)
    // vazava cru pro cliente como HTML de erro do Tomcat/Spring - com stack trace incluso. Esse catch-all
    // garante que TODO erro da API sai no mesmo formato ErrorCustomResponse, mesmo os que a gente não previu.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorCustomResponse> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado");
    }

    private ResponseEntity<ErrorCustomResponse> build(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(new ErrorCustomResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message));
    }
}
