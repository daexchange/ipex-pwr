package ai.turbochain.ipex.wallet.dao;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import ai.turbochain.ipex.wallet.entity.IPEXCoin;


@Repository
public class IPEXCoinDao {
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	/**
	 * 获取PWR代币
	 * @return
	 * @throws Exception
	 */
	public List<IPEXCoin> getPWRToken() throws Exception {
		List<IPEXCoin> ipexCoinLists = jdbcTemplate.query("select * from coin where is_token = 1 and chain_name = 'PWR';",
				new Object[] {}, new BeanPropertyRowMapper<IPEXCoin>(IPEXCoin.class));
		return ipexCoinLists;
	}
}
