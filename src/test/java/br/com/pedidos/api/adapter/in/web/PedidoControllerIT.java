package br.com.pedidos.api.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

/**
 * Teste de integração do POST /pedidos contra o Postgres local (infra/docker-compose.yml).
 * Não roda no `mvnw test` padrão: execute com `.\mvnw.cmd test "-Dtest=*IT"`.
 * Os dados gravados não são apagados.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PedidoControllerIT {

	private static final String CAFE = "{\"clienteId\":\"c-1\",\"itens\":"
			+ "[{\"sku\":\"CAFE-500\",\"quantidade\":2,\"precoUnitario\":18.90}]}";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void garanteQueEstaNoBancoPedidos() {
		assertThat(jdbc.queryForObject("select current_database()", String.class))
				.as("os testes de integração só podem gravar no banco 'pedidos'")
				.isEqualTo("pedidos");
	}

	private MvcResult postar(String corpo) throws Exception {
		return mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
	}

	private int contar(String tabela) {
		return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
	}

	@Test
	@DisplayName("HI1: o pedido criado por HTTP existe no banco, com status ABERTO e o item enviado")
	void criarGravaNoBanco() throws Exception {
		MvcResult resultado = postar(CAFE);
		String corpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
		UUID id = UUID.fromString(JsonPath.<String>read(corpo, "$.id"));

		assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
		assertThat(corpo).contains("\"total\":37.80");
		assertThat(jdbc.queryForObject("select status from pedido where id = ?", String.class, id))
				.isEqualTo("ABERTO");
		List<Map<String, Object>> itens = jdbc.queryForList(
				"select sku, quantidade, preco_unitario from item_pedido where pedido_id = ?", id);
		assertThat(itens).hasSize(1);
		assertThat(itens.get(0)).containsEntry("sku", "CAFE-500").containsEntry("quantidade", 2);
		assertThat((BigDecimal) itens.get(0).get("preco_unitario")).isEqualByComparingTo("18.90");
	}

	@Test
	@DisplayName("HI2: recusas (422, 422 e 400) não adicionam linhas em pedido nem em item_pedido")
	void recusasNaoGravam() throws Exception {
		String quantidadeZero = "{\"clienteId\":\"c-1\",\"itens\":"
				+ "[{\"sku\":\"CAFE-500\",\"quantidade\":0,\"precoUnitario\":18.90}]}";
		String itensVazios = "{\"clienteId\":\"c-1\",\"itens\":[]}";
		String malformado = "{ \"clienteId\": ";
		int pedidosAntes = contar("pedido");
		int itensAntes = contar("item_pedido");

		assertThat(postar(quantidadeZero).getResponse().getStatus()).isEqualTo(422);
		assertThat(postar(itensVazios).getResponse().getStatus()).isEqualTo(422);
		assertThat(postar(malformado).getResponse().getStatus()).isEqualTo(400);

		assertThat(contar("pedido")).isEqualTo(pedidosAntes);
		assertThat(contar("item_pedido")).isEqualTo(itensAntes);
	}

}
