package ai.turbochain.ipex.wallet.component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

import ai.turbochain.ipex.wallet.dao.IPEXCoinDao;
import ai.turbochain.ipex.wallet.entity.Account;
import ai.turbochain.ipex.wallet.entity.Coin;
import ai.turbochain.ipex.wallet.entity.Deposit;
import ai.turbochain.ipex.wallet.entity.IPEXCoin;
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
	@Autowired
	private IPEXCoinDao ipexCoinDao;

	@Override
	public List<Deposit> replayBlock(Long startBlockNumber, Long endBlockNumber) {
		List<Deposit> deposits = new ArrayList<>();
		try {
			for (Long i = startBlockNumber; i <= endBlockNumber; i++) {
				EthBlock block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(i), true).send();
				block.getBlock().getTransactions().stream().forEach(transactionResult -> {
					try {
						EthBlock.TransactionObject transactionObject = (EthBlock.TransactionObject) transactionResult;
						Transaction transaction = transactionObject.get();
						TransactionReceipt transactionReceipt = web3j.ethGetTransactionReceipt(transaction.getHash())
								.send().getResult();
						this.replayPwrTransaction(transaction, transactionReceipt, deposits);
						this.replayPwrTokenTransaction(transaction, transactionReceipt, deposits);
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return deposits;
	}

	/**
	 * 读取区块代币交易数据
	 * 
	 * @param transaction
	 * @param deposits
	 */
	public void replayPwrTokenTransaction(Transaction transaction, TransactionReceipt transactionReceipt,
			List<Deposit> deposits) {
		try {
			List<IPEXCoin> ipexCoinLists = ipexCoinDao.getPWRToken();
			if (transactionReceipt == null || transactionReceipt.isStatusOK() == false
					|| transactionReceipt.getLogs().size() <= 0) {
				return;
			}
			String input = transaction.getInput();
			String cAddress = transaction.getTo();
			for (IPEXCoin ipexCoin : ipexCoinLists) {
				String contractAddress = ipexCoin.getToken_address();
				if (StringUtils.isNotEmpty(input) && input.length() >= 138
						&& contractAddress.equalsIgnoreCase(cAddress)) {
					String data = input.substring(0, 9);
					data = data + input.substring(17, input.length());
					Function function = new Function("transfer", Arrays.asList(),
							Arrays.asList(new TypeReference<Address>() {
							}, new TypeReference<Uint256>() {
							}));
					List<Type> params = FunctionReturnDecoder.decode(data, function.getOutputParameters());
					String toAddress = params.get(0).getValue().toString();
					String amount = params.get(1).getValue().toString();
					if (accountService.isAddressExist(toAddress)) {
						if (StringUtils.isNotEmpty(amount)) {
							Deposit deposit = new Deposit();
							deposit.setTxid(transaction.getHash());
							deposit.setBlockHash(transaction.getBlockHash());
							deposit.setAmount(
									new BigDecimal(amount).divide(BigDecimal.TEN.pow(ipexCoin.getDecimals())));
							deposit.setAddress(toAddress);
							deposit.setTime(Calendar.getInstance().getTime());
							logger.info("receive {} {}", deposit.getAmount(), getCoin().getUnit());
							deposit.setBlockHeight(transaction.getBlockNumber().longValue());
							deposits.add(deposit);
							afterPwrTokenDeposit(deposit, contractAddress, ipexCoin.getDecimals(), ipexCoin.getUnit());
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 充值PWR代币成功后的操作
	 */
	public void afterPwrTokenDeposit(Deposit deposit, String contractAddress, int decimals, String coinName) {
		executorService.execute(new Runnable() {
			public void run() {
				depositPwrToken(deposit, contractAddress, decimals, coinName);
			}
		});
	}

	/**
	 * 充值PWR代币转账到withdraw账户
	 * 
	 * @param deposit
	 */
	public void depositPwrToken(Deposit deposit, String contractAddress, int decimals, String coinName) {
		try {
			BigDecimal fee = ethService.getMinerFee(coin.getGasLimit());
			Account account = accountService.findByAddress(deposit.getAddress());
			if (ethService.getBalance(account.getAddress()).compareTo(fee) < 0) {
				logger.info("地址{}手续费不足，最低为{}" + coin.getUnit(), account.getAddress(), fee);
				ethService.transfer(coin.getKeystorePath() + "/" + coin.getWithdrawWallet(),
						coin.getWithdrawWalletPassword(), deposit.getAddress(), fee, true, "");
				Thread.sleep(1000 * 60 * 15);// 给充值地址转PWR作为手续费，15分钟交易确认
				logger.info("{}手续费不足，转账" + coin.getUnit() + "到充值账户作为手续费:from={},to={},amount={},sync={}",
						deposit.getAddress(), coin.getWithdrawAddress(), deposit.getAddress(), fee, true);
			}
			logger.info("充值代币" + coinName + "转账到withdraw账户:from={},to={},amount={},sync={},withdrawId={}",
					deposit.getAddress(), coin.getWithdrawAddress(), deposit.getAmount(), true, "");
			ethService.transferToken("6MvxHSjAsb", deposit.getAddress(), coin.getWithdrawAddress(), deposit.getAmount(),
					contractAddress, decimals, coinName, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 读取区块交易数据（不考虑代币）
	 * 
	 * @param block
	 * @param deposits
	 * @throws Exception
	 */
	public void replayPwrTransaction(Transaction transaction, TransactionReceipt transactionReceipt,
			List<Deposit> deposits) {
		try {
			if (transactionReceipt == null || transactionReceipt.isStatusOK() == false) {
				return;
			}
			if (StringUtils.isNotEmpty(transaction.getTo()) && accountService.isAddressExist(transaction.getTo())
					&& !transaction.getFrom().equalsIgnoreCase(getCoin().getIgnoreFromAddress())) {// 忽略提现地址
				Deposit deposit = new Deposit();
				deposit.setTxid(transaction.getHash());
				deposit.setBlockHeight(transaction.getBlockNumber().longValue());
				deposit.setBlockHash(transaction.getBlockHash());
				deposit.setAmount(Convert.fromWei(transaction.getValue().toString(), Convert.Unit.ETHER));
				deposit.setAddress(transaction.getTo());
				afterPwrDeposit(deposit);
				deposits.add(deposit);
				logger.info("received coin {} at height {}", transaction.getValue(), transaction.getBlockNumber());
				// 同步余额
				try {
					ethService.syncAddressBalance(deposit.getAddress());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// 如果是地址簿里转出去的地址，需要同步余额
			if (StringUtils.isNotEmpty(transaction.getTo()) && accountService.isAddressExist(transaction.getFrom())) {
				logger.info("sync address:{} balance", transaction.getFrom());
				try {
					ethService.syncAddressBalance(transaction.getFrom());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 充值成功后的操作
	 */
	public void afterPwrDeposit(Deposit deposit) {
		executorService.execute(new Runnable() {
			public void run() {
				depositPwr(deposit);
			}
		});
	}

	/**
	 * 充值PWR转账到withdraw账户(留下0.01作为token提现手续费)
	 * 
	 * @param deposit
	 */
	public void depositPwr(Deposit deposit) {
		try {
			// BigDecimal fee = ethService.getMinerFee(coin.getGasLimit());
			BigDecimal fee = new BigDecimal("0.01");
			Account account = accountService.findByAddress(deposit.getAddress());
			if (ethService.getBalance(account.getAddress()).compareTo(fee) > 0) {
				logger.info("充值" + coin.getUnit() + "转账到withdraw账户:from={},to={},amount={},sync={}",
						deposit.getAddress(), coin.getWithdrawAddress(), deposit.getAmount().subtract(fee), true);
				ethService.transfer(coin.getKeystorePath() + "/" + account.getWalletFile(), "6MvxHSjAsb",
						coin.getWithdrawAddress(), deposit.getAmount().subtract(fee), true, "");
			}
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
