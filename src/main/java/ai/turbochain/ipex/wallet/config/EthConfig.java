package ai.turbochain.ipex.wallet.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;

import ai.turbochain.ipex.wallet.entity.Coin;
import ai.turbochain.ipex.wallet.service.EtherscanApi;
import okhttp3.OkHttpClient;

@Configuration
public class EthConfig {
    @Bean
    @ConditionalOnProperty(name = "coin.keystore-path")
    public Web3j web3j(Coin coin) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(30*1000, TimeUnit.MILLISECONDS);
        builder.writeTimeout(30*1000, TimeUnit.MILLISECONDS);
        builder.readTimeout(30*1000, TimeUnit.MILLISECONDS);
        OkHttpClient httpClient = builder.build();
        Web3j web3j = Web3j.build(new HttpService(coin.getRpc(),httpClient,false));
        return web3j;
    }

    @Bean
    @ConfigurationProperties(prefix = "etherscan")
    public EtherscanApi etherscanApi(){
        EtherscanApi api = new EtherscanApi();
        return api;
    }

    @Bean
    public JsonRpcHttpClient jsonrpcClient(Coin coin) throws MalformedURLException {
        System.out.println("init jsonRpcClient");
        JsonRpcHttpClient jsonrpcClient = new JsonRpcHttpClient(new URL(coin.getRpc()));
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        jsonrpcClient.setHeaders(headers);
        return jsonrpcClient;
    }
}
