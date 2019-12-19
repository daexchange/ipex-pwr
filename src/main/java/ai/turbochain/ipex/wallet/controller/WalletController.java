package ai.turbochain.ipex.wallet.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.utils.Convert;

import ai.turbochain.ipex.wallet.component.EthWatcher;
import ai.turbochain.ipex.wallet.entity.Account;
import ai.turbochain.ipex.wallet.entity.Coin;
import ai.turbochain.ipex.wallet.service.AccountService;
import ai.turbochain.ipex.wallet.service.EthService;
import ai.turbochain.ipex.wallet.util.MessageResult;

@RestController
@RequestMapping("/rpc")
public class WalletController {
	private Logger logger = LoggerFactory.getLogger(WalletController.class);
	@Autowired
	private EthService service;
	@Autowired
	private Web3j web3j;
	@Autowired
	private EthWatcher watcher;
	@Autowired
	private Coin coin;
	@Autowired
	private AccountService accountService;

	@GetMapping("height")
	public MessageResult getHeight() {
		try {
			EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
			long rpcBlockNumber = blockNumber.getBlockNumber().longValue();
			MessageResult result = new MessageResult(0, "success");
			result.setData(rpcBlockNumber);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "查询失败,error:" + e.getMessage());
		}
	}

	@GetMapping("address/{account}")
	public MessageResult getNewAddress(@PathVariable String account,
			@RequestParam(required = false, defaultValue = "6MvxHSjAsb") String password) {
		logger.info("create new account={},password={}", account, password);
		try {
			String address = service.createNewWallet(account, password);
			MessageResult result = new MessageResult(0, "success");
			result.setData(address);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "rpc error:" + e.getMessage());
		}
	}

	@GetMapping("transfer")
	public MessageResult transfer(String address, BigDecimal amount, BigDecimal fee) {
		logger.info("transfer:address={},amount={},fee={}", address, amount, fee);
		try {
			if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
				fee = service.getMinerFee(coin.getGasLimit());
			}
			MessageResult result = service.transferFromWallet(address, amount, fee, coin.getMinCollectAmount());
			logger.info("返回结果 : " + result.toString());
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "error:" + e.getMessage());
		}
	}

	@GetMapping("withdraw")
	public MessageResult withdraw(String username, String address, BigDecimal amount,
			@RequestParam(name = "contractAddress", required = false, defaultValue = "") String contractAddress,
			@RequestParam(name = "decimals", required = false, defaultValue = "18") int decimals,
			@RequestParam(name = "coinName", required = false, defaultValue = "") String coinName,
			@RequestParam(name = "sync", required = false, defaultValue = "true") Boolean sync,
			@RequestParam(name = "withdrawId", required = false, defaultValue = "") String withdrawId) {
		logger.info("withdraw:to={},amount={},sync={},withdrawId={}", address, amount, sync, withdrawId);
		try {
			Account account = accountService.findByName(username);
			if (account == null) {
				return MessageResult.error(500, "用户名不存在:" + username);
			}
			if (contractAddress != null && contractAddress.equals("") == false) {
				return service.transferTokenFromWithdrawWallet("6MvxHSjAsb", account, address, amount, contractAddress,
						decimals, coinName, sync, withdrawId);
			} else {
				return service.transferFromWithdrawWallet("6MvxHSjAsb", account, address, amount, sync, withdrawId);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "error:" + e.getMessage());
		}
	}

	/**
	 * 获取热钱包总额
	 *
	 * @return
	 */
	@GetMapping("balance")
	public MessageResult balance() {
		try {
			BigDecimal balance = accountService.findBalanceSum();
			MessageResult result = new MessageResult(0, "success");
			result.setData(balance);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "查询失败，error:" + e.getMessage());
		}
	}

	@GetMapping("addressBalance")
	public MessageResult getAddressBalance(
			@RequestParam(name = "accountAddress", required = true) String accountAddress,
			@RequestParam(name = "contractAddress", required = true) String contractAddress,
			@RequestParam(name = "decimals", required = true) int decimals) {
		try {
			MessageResult result = new MessageResult(0, "success");
			if (contractAddress != null && contractAddress.equals("") == false) {
				BigDecimal amt = service.getTokenBalance(accountAddress, contractAddress, decimals);
				result.setData(amt);
			} else {
				BigDecimal balance = service.getBalance(accountAddress);
				result.setData(balance);
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "查询失败，error:" + e.getMessage());
		}
	}

	@GetMapping("transaction/{txid}")
	public MessageResult transaction(@PathVariable String txid) throws IOException {
		EthTransaction transaction = web3j.ethGetTransactionByHash(txid).send();
		EthGasPrice gasPrice = web3j.ethGasPrice().send();
		logger.info("gasPrice: " + gasPrice.getGasPrice() + " txnRawResponse: " + transaction.getRawResponse());
		return MessageResult.success("");
	}

	@GetMapping("gas-price")
	public MessageResult gasPrice() throws IOException {
		try {
			BigInteger gasPrice = service.getGasPrice();
			MessageResult result = new MessageResult(0, "success");
			result.setData(Convert.fromWei(gasPrice.toString(), Convert.Unit.GWEI));
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return MessageResult.error(500, "查询失败，error:" + e.getMessage());
		}
	}

	@GetMapping("sync-block")
	public MessageResult manualSync(Long startBlock, Long endBlock) {
		try {
			watcher.replayBlockInit(startBlock, endBlock);
		} catch (IOException e) {
			e.printStackTrace();
			return MessageResult.error(500, "同步失败：" + e.getMessage());
		}
		return MessageResult.success();
	}

	@GetMapping("txn-status")
	public MessageResult isTransactionSuccess(String txid) {
		try {
			Boolean status = service.isTransactionSuccess(txid);
			MessageResult result = new MessageResult(0, "success");
			result.setData(status);
			return result;
		} catch (IOException e) {
			e.printStackTrace();
			return MessageResult.error(500, "交易验证失败：" + e.getMessage());
		}
	}
}
