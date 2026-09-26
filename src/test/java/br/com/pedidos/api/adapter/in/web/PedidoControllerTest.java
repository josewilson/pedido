package br.com.pedidos.api.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

/**
 * Teste unitário do contrato HTTP: sem contexto da aplicação e sem banco.
 * Usa o caso de uso real com uma implementação de Pedidos em memória.
 */
class PedidoControllerTest {

	private static final String CAFE = item("CAFE-500", "2", "18.90");
	private static final String PAO = item("PAO-100", "1", "5.50");

	private final PedidosEmMemoria memoria = new PedidosEmMemoria();

	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new PedidoController(new CriarPedidoService(memoria), new AdicionarItemService(memoria)))
			.setControllerAdvice(new PedidoExceptionHandler())
			.build();

	private static String item(String sku, String quantidade, String preco) {
		return "{\"sku\":\"" + sku + "\",\"quantidade\":" + quantidade + ",\"precoUnitario\":" + preco + "}";
	}

	private static String pedido(String clienteId, String... itens) {
		return "{\"clienteId\":\"" + clienteId + "\",\"itens\":[" + String.join(",", itens) + "]}";
	}

	private MvcResult postar(String corpo) throws Exception {
		return mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
	}

	private static String texto(MvcResult resultado) throws Exception {
		return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	@DisplayName("H1: cria o pedido de c-1 com CAFE-500, 2 x 18.90, e responde 201 com o pedido")
	void criaPedido() throws Exception {
		MvcResult resultado = postar(pedido("c-1", CAFE));
		String corpo = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
		assertThat(UUID.fromString(JsonPath.<String>read(corpo, "$.id"))).isEqualTo(memoria.salvos().get(0).id());
		assertThat(JsonPath.<String>read(corpo, "$.clienteId")).isEqualTo("c-1");
		assertThat(JsonPath.<String>read(corpo, "$.status")).isEqualTo("ABERTO");
		assertThat(JsonPath.<List<?>>read(corpo, "$.itens")).hasSize(1);
		assertThat(JsonPath.<String>read(corpo, "$.itens[0].sku")).isEqualTo("CAFE-500");
		assertThat(JsonPath.<Integer>read(corpo, "$.itens[0].quantidade")).isEqualTo(2);
		assertThat(memoria.salvos()).hasSize(1);
	}

	@Test
	@DisplayName("H2: preço e total viajam como números decimais, não como texto")
	void precoETotalSaoNumeros() throws Exception {
		String corpo = texto(postar(pedido("c-1", CAFE)));

		assertThat(JsonPath.<Object>read(corpo, "$.total")).isInstanceOf(Number.class);
		assertThat(JsonPath.<Object>read(corpo, "$.itens[0].precoUnitario")).isInstanceOf(Number.class);
		assertThat(corpo).contains("\"total\":37.80").contains("\"precoUnitario\":18.90");
	}

	@Test
	@DisplayName("H3: vários itens saem na ordem enviada, e o total soma os subtotais")
	void variosItensEmOrdem() throws Exception {
		MvcResult resultado = postar(pedido("c-1", CAFE, PAO));
		String corpo = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
		assertThat(JsonPath.<String>read(corpo, "$.itens[0].sku")).isEqualTo("CAFE-500");
		assertThat(JsonPath.<String>read(corpo, "$.itens[1].sku")).isEqualTo("PAO-100");
		assertThat(corpo).contains("\"total\":43.30");
	}

	static Stream<Arguments> recusasDeNegocio() {
		return Stream.of(
				arguments("H4: quantidade zero", pedido("c-1", item("CAFE-500", "0", "18.90")),
						"quantidade deve ser maior que zero"),
				arguments("H5: quantidade negativa", pedido("c-1", item("CAFE-500", "-1", "18.90")),
						"quantidade deve ser maior que zero"),
				arguments("H6: preço zero", pedido("c-1", item("CAFE-500", "2", "0")),
						"precoUnitario deve ser maior que zero"),
				arguments("H7: preço com 3 casas", pedido("c-1", item("CAFE-500", "2", "18.905")),
						"precoUnitario aceita no máximo 2 casas decimais"),
				arguments("H8: itens vazios", pedido("c-1"),
						"o pedido precisa de pelo menos um item"));
	}

	@ParameterizedTest(name = "{0} -> 422")
	@MethodSource("recusasDeNegocio")
	void recusaRegraDeNegocioComUnprocessable(String caso, String corpo, String mensagem) throws Exception {
		MvcResult resultado = postar(corpo);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(422);
		assertThat(JsonPath.<Integer>read(resposta, "$.status")).isEqualTo(422);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).isEqualTo(mensagem);
		assertThat(memoria.salvos()).as("H16: recusa não grava").isEmpty();
	}

	static Stream<Arguments> recusasDeFormato() {
		return Stream.of(
				arguments("H10: cliente ausente", "{\"itens\":[" + CAFE + "]}", "clienteId"),
				arguments("H10: cliente em branco", pedido("  ", CAFE), "clienteId"),
				arguments("H11: itens ausente", "{\"clienteId\":\"c-1\"}", "itens"),
				arguments("H12: sku ausente",
						pedido("c-1", "{\"quantidade\":2,\"precoUnitario\":18.90}"), "itens[0].sku"),
				arguments("H12: sku em branco", pedido("c-1", item("", "2", "18.90")), "itens[0].sku"),
				arguments("H13: quantidade ausente",
						pedido("c-1", "{\"sku\":\"CAFE-500\",\"precoUnitario\":18.90}"), "itens[0].quantidade"),
				arguments("H14: preço ausente",
						pedido("c-1", "{\"sku\":\"CAFE-500\",\"quantidade\":2}"), "itens[0].precoUnitario"),
				arguments("H15: item nulo", "{\"clienteId\":\"c-1\",\"itens\":[null]}", "itens[0]"));
	}

	@ParameterizedTest(name = "{0} -> 400")
	@MethodSource("recusasDeFormato")
	void recusaFormatoComBadRequest(String caso, String corpo, String campo) throws Exception {
		MvcResult resultado = postar(corpo);
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
		assertThat(JsonPath.<Integer>read(resposta, "$.status")).isEqualTo(400);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).contains(campo);
		assertThat(memoria.salvos()).as("H16: recusa não grava").isEmpty();
	}

	@Test
	@DisplayName("H9: JSON malformado responde 400 com mensagem genérica")
	void recusaJsonMalformado() throws Exception {
		MvcResult resultado = postar("{ \"clienteId\": ");
		String resposta = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
		assertThat(JsonPath.<String>read(resposta, "$.mensagem")).isEqualTo("JSON malformado ou ilegível");
		assertThat(memoria.salvos()).as("H16: recusa não grava").isEmpty();
	}

}
