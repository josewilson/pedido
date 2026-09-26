package br.com.pedidos.api.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jayway.jsonpath.JsonPath;

import br.com.pedidos.api.PedidosEmMemoria;
import br.com.pedidos.api.application.AdicionarItemService;
import br.com.pedidos.api.application.CriarPedidoService;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

/**
 * Teste unitário do contrato de POST /pedidos/{id}/itens: sem contexto da aplicação e sem banco.
 * Usa os casos de uso reais com uma implementação de Pedidos em memória.
 */
class AdicionarItemControllerTest {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
	private static final String MAIS_UM_CAFE = item("CAFE-500", "1", "18.90");

	private final PedidosEmMemoria memoria = new PedidosEmMemoria();

	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new PedidoController(new CriarPedidoService(memoria), new AdicionarItemService(memoria)))
			.setControllerAdvice(new PedidoExceptionHandler())
			.build();

	private final Pedido existente = Pedido.novo("c-1").adicionarItem(CAFE);

	@BeforeEach
	void guardaOPedidoExistente() {
		memoria.guardar(existente);
	}

	private static String item(String sku, String quantidade, String preco) {
		return "{\"sku\":\"" + sku + "\",\"quantidade\":" + quantidade + ",\"precoUnitario\":" + preco + "}";
	}

	private MvcResult postar(String caminhoId, String corpo) throws Exception {
		return mvc.perform(post("/pedidos/" + caminhoId + "/itens")
				.contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
	}

	private MvcResult postar(UUID id, String corpo) throws Exception {
		return postar(id.toString(), corpo);
	}

	private static String texto(MvcResult resultado) throws Exception {
		return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("W1: adiciona CAFE-500 1 x 18.90 ao pedido de 37.80 e responde 200 com o pedido atualizado")
	void adicionaItem() throws Exception {
		MvcResult resultado = postar(existente.id(), MAIS_UM_CAFE);
		String corpo = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
		assertThat(UUID.fromString(JsonPath.<String>read(corpo, "$.id"))).isEqualTo(existente.id());
		assertThat(JsonPath.<String>read(corpo, "$.clienteId")).isEqualTo("c-1");
		assertThat(JsonPath.<String>read(corpo, "$.status")).isEqualTo("ABERTO");
		assertThat(JsonPath.<List<?>>read(corpo, "$.itens")).hasSize(2);
		assertThat(JsonPath.<Integer>read(corpo, "$.itens[0].quantidade")).isEqualTo(2);
		assertThat(JsonPath.<Integer>read(corpo, "$.itens[1].quantidade")).isEqualTo(1);
		assertThat(corpo).contains("\"total\":56.70");
	}

	@Test
	@DisplayName("W2: preço e total viajam como números decimais, não como texto")
	void precoETotalSaoNumeros() throws Exception {
		String corpo = texto(postar(existente.id(), MAIS_UM_CAFE));

		assertThat(JsonPath.<Object>read(corpo, "$.total")).isInstanceOf(Number.class);
		assertThat(JsonPath.<Object>read(corpo, "$.itens[1].precoUnitario")).isInstanceOf(Number.class);
		assertThat(corpo).contains("\"total\":56.70").contains("\"precoUnitario\":18.90");
	}

	@Test
	@DisplayName("W3: grava o pedido atualizado, uma só entrada com 2 itens")
	void gravaOAtualizado() throws Exception {
		postar(existente.id(), MAIS_UM_CAFE);

		assertThat(memoria.salvamentos()).isEqualTo(1);
		assertThat(memoria.salvos()).hasSize(1);
		assertThat(memoria.salvos().get(0).itens()).hasSize(2);
	}

	@Test
	@DisplayName("W4: pedido inexistente responde 404 com mensagem, e nada é gravado")
	void pedidoInexistente() throws Exception {
		UUID desconhecido = UUID.randomUUID();
		MvcResult resultado = postar(desconhecido, MAIS_UM_CAFE);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
		assertThat(JsonPath.<Integer>read(resposta, "$.status")).isEqualTo(404);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).isEqualTo("pedido não encontrado: " + desconhecido);
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
		assertThat(memoria.salvos()).containsExactly(existente);
	}

	@Test
	@DisplayName("W5: pedido pago responde 409 com mensagem, e nada é gravado")
	void pedidoPago() throws Exception {
		Pedido pago = existente.pagar();
		memoria.guardar(pago);
		MvcResult resultado = postar(pago.id(), MAIS_UM_CAFE);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
		assertThat(JsonPath.<Integer>read(resposta, "$.status")).isEqualTo(409);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).contains("PAGO");
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
		assertThat(memoria.salvos()).containsExactly(pago);
	}

	@Test
	@DisplayName("W6: pedido cancelado responde 409 com mensagem, e nada é gravado")
	void pedidoCancelado() throws Exception {
		Pedido cancelado = existente.cancelar();
		memoria.guardar(cancelado);
		MvcResult resultado = postar(cancelado.id(), MAIS_UM_CAFE);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).contains("CANCELADO");
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
		assertThat(memoria.salvos()).containsExactly(cancelado);
	}

	static Stream<Arguments> itensInvalidos() {
		return Stream.of(
				arguments("W7: quantidade zero", item("CAFE-500", "0", "18.90"),
						"quantidade deve ser maior que zero"),
				arguments("W7: quantidade negativa", item("CAFE-500", "-1", "18.90"),
						"quantidade deve ser maior que zero"),
				arguments("W7: preço zero", item("CAFE-500", "1", "0"),
						"precoUnitario deve ser maior que zero"),
				arguments("W7: preço com 3 casas", item("CAFE-500", "1", "18.905"),
						"precoUnitario aceita no máximo 2 casas decimais"));
	}

	@ParameterizedTest(name = "{0} -> 422")
	@MethodSource("itensInvalidos")
	void recusaItemInvalidoComUnprocessable(String caso, String corpo, String mensagem) throws Exception {
		MvcResult resultado = postar(existente.id(), corpo);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
		assertThat(JsonPath.<Integer>read(resposta, "$.status")).isEqualTo(422);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).isEqualTo(mensagem);
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
		assertThat(memoria.salvos()).containsExactly(existente);
	}

	static Stream<Arguments> formatosInvalidos() {
		return Stream.of(
				arguments("W8: JSON malformado", "{ \"sku\": "),
				arguments("W8: corpo vazio", ""),
				arguments("W8: sku ausente", "{\"quantidade\":1,\"precoUnitario\":18.90}"),
				arguments("W8: sku em branco", item("", "1", "18.90")),
				arguments("W8: quantidade ausente", "{\"sku\":\"CAFE-500\",\"precoUnitario\":18.90}"),
				arguments("W8: preço ausente", "{\"sku\":\"CAFE-500\",\"quantidade\":1}"));
	}

	@ParameterizedTest(name = "{0} -> 400")
	@MethodSource("formatosInvalidos")
	void recusaFormatoComBadRequest(String caso, String corpo) throws Exception {
		MvcResult resultado = postar(existente.id(), corpo);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
		assertThat(JsonPath.<Integer>read(resposta, "$.status")).isEqualTo(400);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).isNotBlank();
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
		assertThat(memoria.salvos()).containsExactly(existente);
	}

	@Test
	@DisplayName("W9: id do caminho que não é UUID responde 400 com mensagem")
	void idQueNaoEUuid() throws Exception {
		MvcResult resultado = postar("abc", MAIS_UM_CAFE);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).contains("UUID");
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
	}

	@Test
	@DisplayName("W10: item inválido vence 404 (pedido inexistente) e 409 (pedido pago): responde 422")
	void itemInvalidoTemPrecedencia() throws Exception {
		String quantidadeZero = item("CAFE-500", "0", "18.90");
		Pedido pago = existente.pagar();
		memoria.guardar(pago);

		assertThat(postar(UUID.randomUUID(), quantidadeZero).getResponse().getStatus()).isEqualTo(422);
		assertThat(postar(pago.id(), quantidadeZero).getResponse().getStatus()).isEqualTo(422);
		assertThat(memoria.salvamentos()).as("W11: recusa não grava").isZero();
	}

}
