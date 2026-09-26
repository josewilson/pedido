package br.com.pedidos.api.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

/**
 * Teste de integração de POST /pedidos/{id}/itens contra o Postgres local (infra/docker-compose.yml).
 * Não roda no `mvnw test` padrão: execute com `.\mvnw.cmd test "-Dtest=*IT"`.
 * Os dados gravados não são apagados.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdicionarItemControllerIT {

	private static final String CRIAR_CAFE = "{\"clienteId\":\"c-1\",\"itens\":"
			+ "[{\"sku\":\"CAFE-500\",\"quantidade\":2,\"precoUnitario\":18.90}]}";
	private static final String MAIS_UM_CAFE = "{\"sku\":\"CAFE-500\",\"quantidade\":1,\"precoUnitario\":18.90}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Pedidos pedidos;

	@BeforeEach
	void garanteQueEstaNoBancoPedidos() {
		assertThat(jdbc.queryForObject("select current_database()", String.class))
				.as("os testes de integração só podem gravar no banco 'pedidos'")
				.isEqualTo("pedidos");
	}

	private MvcResult criar() throws Exception {
		return mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(CRIAR_CAFE)).andReturn();
	}

	private MvcResult adicionar(UUID id, String corpo) throws Exception {
		return mvc.perform(post("/pedidos/" + id + "/itens").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andReturn();
	}

	private static String texto(MvcResult resultado) throws Exception {
		return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private int contar(String consulta, Object... argumentos) {
		return jdbc.queryForObject(consulta, Integer.class, argumentos);
	}

	@Test
	@DisplayName("WI1: 37.80 + 1 x 18.90 = 56.70, no mesmo UUID, com uma linha em pedido e duas em item_pedido")
	void cenarioDaAulaPontaAPonta() throws Exception {
		UUID id = UUID.fromString(JsonPath.<String>read(texto(criar()), "$.id"));

		MvcResult resultado = adicionar(id, MAIS_UM_CAFE);
		String corpo = texto(resultado);

		assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
		assertThat(UUID.fromString(JsonPath.<String>read(corpo, "$.id"))).isEqualTo(id);
		assertThat(corpo).contains("\"total\":56.70");
		assertThat(contar("select count(*) from pedido where id = ?", id)).isEqualTo(1);
		assertThat(contar("select count(*) from item_pedido where pedido_id = ?", id)).isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"select sum(quantidade * preco_unitario) from item_pedido where pedido_id = ?", BigDecimal.class, id))
				.isEqualByComparingTo("56.70");
	}

	@Test
	@DisplayName("WI2: recusas (404, 409, 422 e 400) não adicionam linhas nem alteram os itens dos pedidos")
	void recusasNaoGravam() throws Exception {
		UUID aberto = UUID.fromString(JsonPath.<String>read(texto(criar()), "$.id"));
		Pedido fechado = Pedido.novo("c-1").adicionarItem(new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"))).pagar();
		pedidos.salvar(fechado);
		String quantidadeZero = "{\"sku\":\"CAFE-500\",\"quantidade\":0,\"precoUnitario\":18.90}";
		int pedidosAntes = contar("select count(*) from pedido");
		int itensAntes = contar("select count(*) from item_pedido");

		assertThat(adicionar(UUID.randomUUID(), MAIS_UM_CAFE).getResponse().getStatus()).isEqualTo(404);
		assertThat(adicionar(fechado.id(), MAIS_UM_CAFE).getResponse().getStatus()).isEqualTo(409);
		assertThat(adicionar(aberto, quantidadeZero).getResponse().getStatus()).isEqualTo(422);
		assertThat(adicionar(aberto, "{ \"sku\": ").getResponse().getStatus()).isEqualTo(400);

		assertThat(contar("select count(*) from pedido")).isEqualTo(pedidosAntes);
		assertThat(contar("select count(*) from item_pedido")).isEqualTo(itensAntes);
		assertThat(contar("select count(*) from item_pedido where pedido_id = ?", aberto)).isEqualTo(1);
		assertThat(contar("select count(*) from item_pedido where pedido_id = ?", fechado.id())).isEqualTo(1);
	}

}
