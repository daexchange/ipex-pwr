package ai.turbochain.ipex.wallet.component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Convert;

import ai.turbochain.ipex.wallet.entity.Account;
import ai.turbochain.ipex.wallet.entity.Coin;
import ai.turbochain.ipex.wallet.entity.Deposit;
import ai.turbochain.ipex.wallet.event.DepositEvent;
import ai.turbochain.ipex.wallet.service.AccountService;
import ai.turbochain.ipex.wallet.service.EthService;

@Component
public class EthWatcher extends Watcher {
	private Logger logger = LoggerFactory.getLogger(EthWatcher.class);
	@Autowired
	private Web3j web3j;
	@Autowired
	private EthService ethService;
	@Autowired
	private Coin coin;
	@Autowired
	private AccountService accountService;
	@Autowired
	private DepositEvent depositEvent;
	@Autowired
	private ExecutorService executorService;

	@Override
	public List<Deposit> replayBlock(Long startBlockNumber, Long endBlockNumber) {
		List<Deposit> deposits = new ArrayList<>();
		try {
			for (Long i = startBlockNumber; i <= endBlockNumber; i++) {
				EthBlock block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(i), true).send();

				block.getBlock().getTransactions().stream().forEach(transactionResult -> {
					EthBlock.TransactionObject transactionObject = (EthBlock.TransactionObject) transactionResult;
					Transaction transaction = transactionObject.get();
					if (StringUtils.isNotEmpty(transaction.getTo())
							&& accountService.isAddressExist(transaction.getTo())
							&& !transaction.getFrom().equalsIgnoreCase(getCoin().getIgnoreFromAddress())) {// 忽略提现地址
						Deposit deposit = new Deposit();
						deposit.setTxid(transaction.getHash());
						deposit.setBlockHeight(transaction.getBlockNumber().longValue());
						deposit.setBlockHash(transaction.getBlockHash());
						deposit.setAmount(Convert.fromWei(transaction.getValue().toString(), Convert.Unit.ETHER));
						deposit.setAddress(transaction.getTo());
						afterDeposit(deposit);
						deposits.add(deposit);
						logger.info("received coin {} at height {}", transaction.getValue(),
								transaction.getBlockNumber());
						// 同步余额
						try {
							ethService.syncAddressBalance(deposit.getAddress());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					// 如果是地址簿里转出去的地址，需要同步余额
					if (StringUtils.isNotEmpty(transaction.getTo())
							&& accountService.isAddressExist(transaction.getFrom())) {
						logger.info("sync address:{} balance", transaction.getFrom());
						try {
							ethService.syncAddressBalance(transaction.getFrom());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return deposits;
	}

	/**
	 * 注册成功后的操作
	 */
	public void afterDeposit(Deposit deposit) {
		executorService.execute(new Runnable() {
			public void run() {
				depositCoin(deposit);
			}
		});
	}

	/**
	 * 充值PWR转账到withdraw账户
	 * 
	 * @param deposit
	 */
	public void depositCoin(Deposit deposit) {
		try {
			BigDecimal fee = ethService.getMinerFee(coin.getGasLimit());
			Account account = accountService.findByAddress(deposit.getAddress());
			logger.info("充值PWR转账到withdraw账户:from={},to={},amount={},sync={}", deposit.getAddress(),
					coin.getWithdrawAddress(), deposit.getAmount().subtract(fee), true);
			ethService.transfer(coin.getKeystorePath() + "/" + account.getWalletFile(), "", coin.getWithdrawAddress(),
					deposit.getAmount().subtract(fee), true, "");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized int replayBlockInit(Long startBlockNumber, Long endBlockNumber) throws IOException {
		int count = 0;
		for (Long i = startBlockNumber; i <= endBlockNumber; i++) {
			EthBlock block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(i), true).send();

			block.getBlock().getTransactions().stream().forEach(transactionResult -> {
				EthBlock.TransactionObject transactionObject = (EthBlock.TransactionObject) transactionResult;
				Transaction transaction = transactionObject.get();
				if (StringUtils.isNotEmpty(transaction.getTo()) && accountService.isAddressExist(transaction.getTo())
						&& !transaction.getFrom().equalsIgnoreCase(getCoin().getIgnoreFromAddress())) {
					Deposit deposit = new Deposit();
					deposit.setTxid(transaction.getHash());
					deposit.setBlockHeight(transaction.getBlockNumber().longValue());
					deposit.setBlockHash(transaction.getBlockHash());
					deposit.setAmount(Convert.fromWei(transaction.getValue().toString(), Convert.Unit.ETHER));
					deposit.setAddress(transaction.getTo());
					logger.info("received coin {} at height {}", transaction.getValue(), transaction.getBlockNumber());
					depositEvent.onConfirmed(deposit);
					// 同步余额
					try {
						ethService.syncAddressBalance(deposit.getAddress());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				// 如果是地址簿里转出去的地址，需要同步余额
				if (StringUtils.isNotEmpty(transaction.getTo())
						&& accountService.isAddressExist(transaction.getFrom())) {
					logger.info("sync address:{} balance", transaction.getFrom());
					try {
						ethService.syncAddressBalance(transaction.getFrom());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		}
		return count;
	}

	@Override
	public Long getNetworkBlockHeight() {
		try {
			EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
			return blockNumber.getBlockNumber().longValue();
		} catch (Exception e) {
			e.printStackTrace();
			return 0L;
		}
	}
}
