/**
 * Copyright 2019 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */
package com.forgerock.openbanking.tpp.core.scheduler;

import com.forgerock.openbanking.tpp.core.configuration.TppClientsConfiguration;
import com.forgerock.openbanking.tpp.core.model.PaymentEvent;
import com.forgerock.openbanking.tpp.core.model.PaymentEventsNotification;
import com.forgerock.openbanking.tpp.core.repository.PaymentEventsRepository;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.forgerock.openbanking.constants.OpenBankingConstants.BOOKED_TIME_DATE_FORMAT;

@Component
public class NotifyPaymentEventsToMerchantTask {

    private final static Logger LOGGER = LoggerFactory.getLogger(NotifyPaymentEventsToMerchantTask.class);
    private final static DateTimeFormatter format = DateTimeFormat.forPattern(BOOKED_TIME_DATE_FORMAT);

    @Autowired
    private PaymentEventsRepository paymentEventsRepository;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private TppClientsConfiguration tppClientsConfiguration;

    @Scheduled(fixedRate =  60 * 1000)
    @SchedulerLock(name = "notifyMerchant")
    public void notifyMerchant() {
        LOGGER.info("Notifying merchant task waking up. The time is now {}.", format.print(DateTime.now()));

        Collection<PaymentEvent> paymentEvents = paymentEventsRepository.findAll();

        //Clean up wrong payment request
        for (PaymentEvent paymentEvent: paymentEvents) {
            if (paymentEvent.getPaymentRequest() == null) {
                paymentEventsRepository.delete(paymentEvent);
            }
        }
        paymentEvents = paymentEventsRepository.findAll();
        if (paymentEvents.isEmpty()) {
            LOGGER.info("Nothing to notify. See you in 5 minutes! The time is now {}.", format.print(DateTime.now()));
            return;
        }

        Map<String, List<PaymentEvent>> paymentEventsByMerchant = paymentEvents.stream()
                .collect(Collectors.groupingBy( p -> p.getPaymentRequest().getMerchantId()));

        LOGGER.info("There is '{}' event to compute for {} merchants", paymentEvents.size(), paymentEventsByMerchant.keySet().size());

        List<PaymentEvent> paymentEventsSendWithSuccess = new ArrayList<>();
        for (Map.Entry<String,  List<PaymentEvent>> entry : paymentEventsByMerchant.entrySet()) {
            String merchantId = entry.getKey();
            List<PaymentEvent> paymentStatusRequestsForMerchant = entry.getValue();
            PaymentEventsNotification paymentEventsNotification = new PaymentEventsNotification(paymentStatusRequestsForMerchant);

            HttpEntity<PaymentEventsNotification> request = new HttpEntity<>(paymentEventsNotification, new HttpHeaders());

            try {
                restTemplate.postForLocation(tppClientsConfiguration.getStatusCallback().get(merchantId), request);
                paymentEventsSendWithSuccess.addAll(paymentStatusRequestsForMerchant);
            } catch (RestClientException e) {
                LOGGER.warn("Couldn't send notification to merchant ID {}", merchantId, e);
                continue;
            }
        }
        LOGGER.info("They were {} events to compute, {} have been send successfully.",
                paymentEvents.size(), paymentEventsSendWithSuccess.size());
        paymentEventsRepository.deleteAll(paymentEventsSendWithSuccess);
        LOGGER.info("All events notified. See you in 5 minutes! The time is now {}.", format.print(DateTime.now()));
    }
}
