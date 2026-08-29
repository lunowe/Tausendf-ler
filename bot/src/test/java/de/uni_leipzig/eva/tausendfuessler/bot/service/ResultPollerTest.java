package de.uni_leipzig.eva.tausendfuessler.bot.service;

import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobDetail;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.JobStatus;
import de.uni_leipzig.eva.tausendfuessler.bot.dto.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ResultPollerTest {

    private final CoordinatorClient client = mock(CoordinatorClient.class);
    private final MessageSender sender = mock(MessageSender.class);
    private final ResultPoller poller = new ResultPoller(client, sender);

    @Test
    void drainsAllPagesOfACompletedJobBeforeSendingTheReport() {
        // 120 pages exist, the coordinator hands out 50 per call
        List<PageResult> all = pages(1, 120);
        when(client.getNewResults(eq("job"), anyLong())).thenAnswer(inv -> {
            long after = inv.getArgument(1);
            return all.stream().filter(p -> p.getSeq() > after).limit(ResultPoller.RESULT_PAGE_SIZE).toList();
        });
        JobDetail detail = new JobDetail();
        detail.setJobId("job");
        detail.setStatus(JobStatus.COMPLETED);
        detail.setPagesVisited(120);
        when(client.getJobDetail("job")).thenReturn(detail);

        poller.subscribe(42L, "job");
        poller.pollForNewResults();

        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.atLeast(2)).send(eq(42L), texts.capture());
        String stream = String.join("\n", texts.getAllValues());
        for (long seq = 1; seq <= 120; seq++) {
            assertThat(stream).contains("https://example.test/p" + seq + "\n");
        }
        assertThat(texts.getAllValues().get(texts.getAllValues().size() - 1)).contains("Crawl abgeschlossen");
        // batched: far fewer Telegram messages than pages
        assertThat(texts.getAllValues().size()).isLessThan(30);
        assertThat(texts.getAllValues()).allSatisfy(t -> assertThat(t.length()).isLessThanOrEqualTo(4096));

        poller.pollForNewResults(); // unsubscribed: no further calls
        verify(client, org.mockito.Mockito.times(1)).getJobDetail("job");
    }

    private static List<PageResult> pages(long from, long to) {
        List<PageResult> list = new ArrayList<>();
        LongStream.rangeClosed(from, to).forEach(seq -> {
            PageResult p = new PageResult();
            p.setSeq(seq);
            p.setUrl("https://example.test/p" + seq);
            p.setTitle("Page " + seq);
            p.setTextSnippet("x".repeat(200));
            p.setDepth(1);
            list.add(p);
        });
        return list;
    }
}
