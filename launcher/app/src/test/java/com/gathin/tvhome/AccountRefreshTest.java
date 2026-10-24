package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.gathin.tvhome.AccountRefresh.Trigger.*;
public class AccountRefreshTest {
 @Test public void tickWhileHomeStaysVisibleReadsAtMostEveryFifteenMinutes() { AccountRefresh p=new AccountRefresh();long t=1000000;assertTrue(p.due(TICK,t));p.started(t);p.succeeded();assertFalse(p.due(TICK,t+14*60000));assertTrue(p.due(TICK,t+15*60000)); }
 @Test public void returningToHomeRefreshesAfterAMinute() { AccountRefresh p=new AccountRefresh();p.started(0);p.succeeded();assertFalse(p.due(FOREGROUND,59999));assertTrue(p.due(FOREGROUND,60000)); }
 @Test public void failuresBackOffExponentiallyUpToThirtyMinutesAndForceBypasses() { AccountRefresh p=new AccountRefresh();long now=0;for(long delay:new long[]{120000,240000,480000,960000,1800000,1800000}) {p.started(now);p.failed(now,-1);assertFalse(p.due(FOREGROUND,now+delay-1));assertTrue(p.due(TICK,now+delay));assertTrue(p.due(FORCE,now));now+=delay;}p.succeeded();assertTrue(p.due(FOREGROUND,now)); }
 @Test public void afterAFailureTheMinuteTickRetriesWhenTheBackoffEnds() { AccountRefresh p=new AccountRefresh();p.started(0);p.failed(0,-1);assertFalse(p.due(TICK,119000));assertTrue(p.due(TICK,120000)); }
 @Test public void rateLimitWaitsAtLeastTenMinutes() { for(int code:new int[]{429,503}) {AccountRefresh p=new AccountRefresh();p.started(0);p.failed(0,code);assertFalse(p.due(FOREGROUND,599999));assertTrue(p.due(FOREGROUND,600000));} }
 @Test public void resetAfterAccountChangeAllowsAnImmediateRead() { AccountRefresh p=new AccountRefresh();p.started(0);p.failed(0,429);p.reset();assertTrue(p.due(TICK,1)); }
}
