package ai.turbochain.ipex.wallet.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import ai.turbochain.ipex.wallet.entity.Coin;
import ai.turbochain.ipex.wallet.entity.Payment;
import ai.turbochain.ipex.wallet.util.MessageResult;

/**
 * ETH与Token付款模块，支持同步任务与异步任务，在单地址可能出现连续付款的情况的下，使用异步阶列
 */
@Component
public class PaymentHandler {
	private Logger logger = LoggerFactory.getLogger(PaymentHandler.class);
	@Autowired
	private Web3j web3j;
	@Autowired
	private EthService ethService;
	@Autowired
	private Coin coin;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired(required = false)
	private EtherscanApi etherscanApi;
	private Payment current;
	private LinkedList<Payment> tasks = new LinkedList<>();
	private int checkTimes = 0;
	private int maxCheckTimes = 100;

	public void transferTokenAsync(Credentials credentials, String to, BigDecimal amount, String contractAddress,
			int decimals, String coinName, String withdrawId) {
		Payment payment = Payment.builder().credentials(credentials).amount(amount).to(to).txBizNumber(withdrawId)
				.unit(coinName).contractAddress(contractAddress).decimals(decimals).build();
		synchronized (tasks) {
			tasks.addLast(payment);
		}
	}

	public void notify(Payment payment, int status) {
		JSONObject json = new JSONObject();
		json.put("withdrawId", payment.getTxBizNumber());
		json.put("txid", payment.getTxid());
		json.put("status", status);
		kafkaTemplate.send("withdraw-notify", coin.getName(), JSON.toJSONString(json));
	}

	public void transferPwrAsync(Credentials credentials, String to, BigDecimal amount, String withdrawId) {
		Payment payment = Payment.builder().credentials(credentials).amount(amount).to(to).txBizNumber(withdrawId)
				.unit(coin.getUnit()).build();
		synchronized (tasks) {
			tasks.addLast(payment);
		}
	}

	public MessageResult transferPwr(Credentials credentials, String to, BigDecimal amount) {
		Payment payment = Payment.builder().credentials(credentials).amount(amount).to(to).unit(coin.getUnit()).build();
		return transferPwr(payment);
	}

	public MessageResult transferPwr(Payment payment) {
		try {
			EthGetTransactionCount ethGetTransactionCount = web3j
					.ethGetTransactionCount(payment.getCredentials().getAddress(), DefaultBlockParameterName.LATEST)
					.sendAsync().get();

			BigInteger nonce = ethGetTransactionCount.getTransactionCount();
			BigInteger gasPrice = ethService.getGasPrice();
			BigInteger value = Convert.toWei(payment.getAmount(), Convert.Unit.ETHER).toBigInteger();

			BigInteger maxGas = coin.getGasLimit();
			logger.info("value={},gasPrice={},gasLimit={},nonce={},address={}", value, gasPrice, maxGas, nonce,
					payment.getTo());
			RawTransaction rawTransaction = RawTransaction.createEtherTransaction(nonce, gasPrice, maxGas,
					payment.getTo(), value);

			byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, payment.getCredentials());
			String hexValue = Numeric.toHexString(signedMessage);
			EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();
			String transactionHash = ethSendTransaction.getTransactionHash();
			logger.info("txid = {}", transactionHash);
			if (StringUtils.isEmpty(transactionHash)) {
				return new MessageResult(500, "发送交易失败");
			} else {
				if (etherscanApi != null) {
					logger.info("=====发送Etherscan广播交易======");
					// etherscanApi.sendRawTransaction(hexValue);
				}
				MessageResult mr = new MessageResult(0, "success");
				mr.setData(transactionHash);
				return mr;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new MessageResult(500, "交易失败,error:" + e.getMessage());
		}
	}

	public static void main(String[] args) {
		System.out.println(Convert.toWei("2", Convert.Unit.ETHER).toBigInteger());
	}
	
	public MessageResult transferToken(Payment payment) {
		try {
			EthGetTransactionCount ethGetTransactionCount = web3j
					.ethGetTransactionCount(payment.getCredentials().getAddress(), DefaultBlockParameterName.LATEST)
					.sendAsync().get();
			BigInteger nonce = ethGetTransactionCount.getTransactionCount();
			BigInteger gasPrice = ethService.getGasPrice();
			BigInteger value = payment.getAmount().multiply(new BigDecimal("10").pow(payment.getDecimals())).toBigInteger();
			Function fn = new Function("transfer", Arrays.asList(new Address(payment.getTo()), new Uint256(value)),
					Collections.<TypeReference<?>>emptyList());
			String data = FunctionEncoder.encode(fn);
			BigInteger maxGas = coin.getGasLimit();
			logger.info("from={},value={},gasPrice={},gasLimit={},nonce={},address={}",
					payment.getCredentials().getAddress(), value, gasPrice, maxGas, nonce, payment.getTo());
			RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, maxGas,
					payment.getContractAddress(), data);
			byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, payment.getCredentials());
			String hexValue = Numeric.toHexString(signedMessage);
			logger.info("hexRawValue={}", hexValue);
			EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();
			String transactionHash = ethSendTransaction.getTransactionHash();
			logger.info("txid:" + transactionHash);
			if (StringUtils.isEmpty(transactionHash)) {
				return new MessageResult(500, "发送交易失败");
			} else {
				if (etherscanApi != null) {
					logger.info("=====发送Etherscan广播交易======");
					// etherscanApi.sendRawTransaction(hexValue);
				}
				payment.setTxid(transactionHash);
				MessageResult mr = new MessageResult(0, "success");
				mr.setData(transactionHash);
				return mr;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new MessageResult(500, "交易失败,error:" + e.getMessage());
		}
	}

	public MessageResult transferToken(Credentials credentials, String to, BigDecimal amount, String contractAddress,
			int decimals, String coinName) {
		Payment payment = Payment.builder().credentials(credentials).amount(amount).to(to).unit(coinName)
				.contractAddress(contractAddress).decimals(decimals).build();
		return transferToken(payment);
	}

	/**
	 * 检查当前任务是否支付完成
	 */
	@Scheduled(cron = "0/30 * * * * *")
	public synchronized void checkJob() {
		logger.info("检查付款任务状态");
//        && StringUtils.isNotEmpty(current.getTxid())
		if (current != null) {
			synchronized (current) {
				try {
					checkTimes++;
					if (ethService.isTransactionSuccess(current.getTxid())) {
						logger.info("转账{}已成功,检查次数:{}", JSON.toJSON(current), checkTimes);
						notify(current, 1);
						current = null;
					} else {
						logger.info("转账{}未成功,检查次数:{}", JSON.toJSON(current), checkTimes);
						if (checkTimes > maxCheckTimes) {
							// 超时未成功
							notify(current, 0);
							current = null;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			logger.info("无待确认的任务");
		}
	}

	public MessageResult transfer(Payment payment) {
		if (payment.getUnit().equalsIgnoreCase(coin.getUnit())) {
			return transferPwr(payment);
		} else {
			return transferToken(payment);
		}
	}

	@Scheduled(cron = "0/30 * * * * *")
	public synchronized void doJob() {
		synchronized (tasks) {
			logger.info("开始执行付款任务，当前队列长度{}", tasks.size());
			if (current == null && tasks.size() > 0) {
				logger.info("开始执行付款任务:current---" + JSONObject.toJSONString(current));
				Payment payment = tasks.getFirst();
				MessageResult result = transfer(payment);
				if (result.getCode() == 0) {
					logger.info("------txID:" + result.getData().toString());
					payment.setTxid(result.getData().toString());
					tasks.removeFirst();
					current = payment;
					checkTimes = 0;
				}
			}
		}
	}
}
