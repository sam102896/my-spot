package com.spot.trade.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.common.api.ApiException;
import com.spot.config.AppProperties;
import com.spot.trade.application.TradeApplicationService;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.service.AeronTradeMessages.TradeCommandMessage;
import com.spot.trade.service.AeronTradeMessages.TradeResultMessage;
import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import java.nio.charset.StandardCharsets;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AeronTradeTransport implements SmartLifecycle {
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final AeronTradeReplyCoordinator replyCoordinator;
    private final TradeApplicationService applicationService;

    private volatile boolean running;
    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Publication commandPublication;
    private Publication resultPublication;
    private Subscription commandSubscription;
    private Subscription resultSubscription;
    private Thread commandPoller;
    private Thread resultPoller;

    public AeronTradeTransport(AppProperties appProperties, ObjectMapper objectMapper,
            AeronTradeReplyCoordinator replyCoordinator, TradeApplicationService applicationService) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.replyCoordinator = replyCoordinator;
        this.applicationService = applicationService;
    }

    @Override
    public synchronized void start() {
        if (running || !config().isEnabled()) {
            return;
        }

        AppProperties.Aeron cfg = config();
        try {
            if (cfg.isEmbeddedDriver()) {
                MediaDriver.Context driverContext = new MediaDriver.Context();
                if (!cfg.getDirectoryName().isBlank()) {
                    driverContext.aeronDirectoryName(cfg.getDirectoryName());
                }
                mediaDriver = MediaDriver.launchEmbedded(driverContext);
            }

            String directoryName = resolveDirectoryName(cfg);
            aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(directoryName));
            commandPublication = aeron.addPublication(cfg.getCommandChannel(), cfg.getCommandStreamId());
            resultPublication = aeron.addPublication(cfg.getResultChannel(), cfg.getResultStreamId());
            commandSubscription = aeron.addSubscription(cfg.getCommandChannel(), cfg.getCommandStreamId());
            resultSubscription = aeron.addSubscription(cfg.getResultChannel(), cfg.getResultStreamId());

            running = true;
            commandPoller = startPoller("aeron-trade-command", commandSubscription,
                    new FragmentAssembler(commandHandler()), cfg.getFragmentLimit());
            resultPoller = startPoller("aeron-trade-result", resultSubscription, new FragmentAssembler(resultHandler()),
                    cfg.getFragmentLimit());
        } catch (Exception exception) {
            stop();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRADE_AERON_START_FAILED", rootMessage(exception));
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        stopThread(commandPoller);
        stopThread(resultPoller);
        commandPoller = null;
        resultPoller = null;
        CloseHelper.quietClose(commandSubscription);
        CloseHelper.quietClose(resultSubscription);
        CloseHelper.quietClose(commandPublication);
        CloseHelper.quietClose(resultPublication);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
        commandSubscription = null;
        resultSubscription = null;
        commandPublication = null;
        resultPublication = null;
        aeron = null;
        mediaDriver = null;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return config().isEnabled();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public void sendCommand(TradeCommandMessage command) {
        ensureReady();
        publish(commandPublication, write(command), "TRADE_AERON_COMMAND_FAILED", "Aeron 交易命令发送失败");
    }

    private Thread startPoller(String name, Subscription subscription, FragmentAssembler assembler, int fragmentLimit) {
        Thread thread = new Thread(() -> pollLoop(subscription, assembler, fragmentLimit), name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void pollLoop(Subscription subscription, FragmentAssembler assembler, int fragmentLimit) {
        while (running) {
            int fragments = subscription.poll(assembler, fragmentLimit);
            if (fragments <= 0) {
                sleepQuietly(config().getIdleSleepMs());
            }
        }
    }

    private FragmentHandler commandHandler() {
        return (buffer, offset, length, header) -> {
            TradeCommandMessage command = read(buffer, offset, length, TradeCommandMessage.class);
            TradeResultMessage result;
            try {
                OrderEntity order = switch (command.action()) {
                    case "PLACE" -> applicationService.placeOrder(command.placeOrder().toCommand(),
                            TradeEngineType.AERON.name());
                    case "CANCEL" -> applicationService.cancelOrder(command.cancelOrder().toCommand(),
                            TradeEngineType.AERON.name());
                    default -> throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_ACTION_UNSUPPORTED",
                            "不支持的 Aeron 交易命令");
                };
                result = TradeResultMessage.success(command.requestId(), order);
            } catch (ApiException exception) {
                result = TradeResultMessage.failure(command.requestId(), exception);
            } catch (Exception exception) {
                result = TradeResultMessage.failure(command.requestId(), exception);
            }
            publish(resultPublication, write(result), "TRADE_AERON_RESULT_FAILED", "Aeron 交易结果发送失败");
        };
    }

    private FragmentHandler resultHandler() {
        return (buffer, offset, length, header) -> replyCoordinator.complete(read(buffer, offset, length,
                TradeResultMessage.class));
    }

    private <T> T read(org.agrona.DirectBuffer buffer, int offset, int length, Class<T> type) {
        byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);
        try {
            return objectMapper.readValue(bytes, type);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_AERON_DESERIALIZE_FAILED", "Aeron 交易消息解析失败");
        }
    }

    private byte[] write(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TRADE_AERON_SERIALIZE_FAILED",
                    "Aeron 交易消息序列化失败");
        }
    }

    private void publish(Publication publication, byte[] bytes, String errorCode, String fallbackMessage) {
        UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        long result = Publication.NOT_CONNECTED;
        for (int i = 0; i < config().getOfferRetryCount(); i++) {
            result = publication.offer(buffer, 0, bytes.length);
            if (result > 0) {
                return;
            }
            sleepQuietly(config().getIdleSleepMs());
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, errorCode, fallbackMessage + ": " + offerResult(result));
    }

    private void ensureReady() {
        if (!config().isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRADE_AERON_DISABLED", "当前未启用 Aeron 交易引擎");
        }
        if (!running) {
            start();
        }
    }

    private String resolveDirectoryName(AppProperties.Aeron cfg) {
        if (mediaDriver != null) {
            return mediaDriver.aeronDirectoryName();
        }
        if (!cfg.getDirectoryName().isBlank()) {
            return cfg.getDirectoryName();
        }
        return CommonContext.getAeronDirectoryName();
    }

    private AppProperties.Aeron config() {
        return appProperties.getTrading().getAeron();
    }

    private void stopThread(Thread thread) {
        if (thread == null) {
            return;
        }
        thread.interrupt();
        try {
            thread.join(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(int sleepMs) {
        if (sleepMs <= 0) {
            Thread.onSpinWait();
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String offerResult(long result) {
        return switch ((int) result) {
            case (int) Publication.NOT_CONNECTED -> "NOT_CONNECTED";
            case (int) Publication.ADMIN_ACTION -> "ADMIN_ACTION";
            case (int) Publication.BACK_PRESSURED -> "BACK_PRESSURED";
            case (int) Publication.CLOSED -> "CLOSED";
            case (int) Publication.MAX_POSITION_EXCEEDED -> "MAX_POSITION_EXCEEDED";
            default -> Long.toString(result);
        };
    }

    private String rootMessage(Exception exception) {
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return new String(exception.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
