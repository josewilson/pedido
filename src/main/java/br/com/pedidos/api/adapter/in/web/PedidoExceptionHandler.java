package br.com.pedidos.api.adapter.in.web;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import br.com.pedidos.api.application.PedidoNaoEncontradoException;
import br.com.pedidos.api.application.PedidoSemItensException;
import br.com.pedidos.api.domain.ItemInvalidoException;
import br.com.pedidos.api.domain.PedidoFechadoException;

/**
 * Traduz para HTTP só as recusas conhecidas de POST /pedidos e POST /pedidos/{id}/itens:
 * regra de negócio vira 422, pedido inexistente vira 404, pedido fechado vira 409,
 * e corpo ilegível, campo ausente ou id que não é UUID vira 400.
 */
@RestControllerAdvice
public class PedidoExceptionHandler {

	public record Erro(int status, String mensagem) {
	}

	@ExceptionHandler({ ItemInvalidoException.class, PedidoSemItensException.class })
	ResponseEntity<Erro> regraDeNegocio(IllegalArgumentException excecao) {
		return ResponseEntity.status(422).body(new Erro(422, excecao.getMessage()));
	}

	@ExceptionHandler(PedidoNaoEncontradoException.class)
	ResponseEntity<Erro> pedidoNaoEncontrado(PedidoNaoEncontradoException excecao) {
		return ResponseEntity.status(404).body(new Erro(404, excecao.getMessage()));
	}

	@ExceptionHandler(PedidoFechadoException.class)
	ResponseEntity<Erro> pedidoFechado(PedidoFechadoException excecao) {
		return ResponseEntity.status(409).body(new Erro(409, excecao.getMessage()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Erro> identificadorInvalido(MethodArgumentTypeMismatchException excecao) {
		return ResponseEntity.badRequest()
				.body(new Erro(400, "Identificador inválido: '" + excecao.getName() + "' deve ser um UUID"));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Erro> jsonIlegivel(HttpMessageNotReadableException excecao) {
		return ResponseEntity.badRequest().body(new Erro(400, "JSON malformado ou ilegível"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Erro> camposInvalidos(MethodArgumentNotValidException excecao) {
		String campos = excecao.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getField)
				.distinct()
				.sorted()
				.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest()
				.body(new Erro(400, "Campos obrigatórios ausentes ou em branco: " + campos));
	}

}
