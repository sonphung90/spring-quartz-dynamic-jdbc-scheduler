package com.nadeem.app.scheduler;

import java.util.Date;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.support.ArgumentConvertingMethodInvoker;
import org.springframework.util.Assert;
import org.springframework.util.MethodInvoker;

public class BaseDynamicScheduler implements InitializingBean
{
    private static final String TARGET_BEAN = "targetBean";
    private static final String METHOD_NAME_KEY = "method";
    private static final String ARGUMENTS_KEY = "arguments";

    private Scheduler scheduler;
    private Object targetBean;
    private String targetMethod;
    private String group;

    public BaseDynamicScheduler(final Scheduler newScheduler)
    {
        this.scheduler = newScheduler;
    }

    @Override
    public void afterPropertiesSet() throws Exception
    {
        Assert.notNull(scheduler, "Scheduler must be set.");
        Assert.notNull(targetBean, "Bean should not be null.");
        Assert.hasText(targetMethod, "Method name should not be blank.");
        Assert.hasText(group, "Group property must not be empty");
    }

    public void scheduleInvocation(final String jobName, final Date when, final Object[] args)
    {
        SimpleTrigger trigger = new SimpleTrigger(jobName, this.group, when);
        trigger.setJobName(jobName);
        trigger.setJobGroup(this.group);
        doSchedule(createJobDetail(args, jobName), trigger);
    }

    public void scheduleWithInterval(final String jobName, final Integer frequencyInMins, final Object[] args)
    {
        if (frequencyInMins <= 0)
        {
            throw new IllegalArgumentException("Frequency interval should be a positive integer.");
        }
        SimpleTrigger trigger = new SimpleTrigger(jobName, this.group, new Date());
        trigger.setRepeatInterval(frequencyInSeconds(frequencyInMins));
        trigger.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
        trigger.setJobName(jobName);
        trigger.setJobGroup(this.group);
        doSchedule(createJobDetail(args, jobName), trigger);
    }

    public void removeScheduler(final String jobName)
    {
        try
        {
            scheduler.removeTriggerListener(jobName);
        }
        catch (SchedulerException e)
        {
            throw new IllegalStateException("Failed to schedule the Job.");
        }
    }

    private int frequencyInSeconds(final Integer frequencyInMins)
    {
        return frequencyInMins * 60 * 1000;
    }

    private void doSchedule(final JobDetail job, final Trigger trigger)
    {
        if (isJobExists(job))
        {
            rescheduleJob(job, trigger);
        }
        else
        {
            scheduleJob(job, trigger);
        }
    }

    private boolean isJobExists(final JobDetail job)
    {
        try
        {
            return this.scheduler.getJobDetail(job.getName(), this.group) != null;
        }
        catch (SchedulerException e)
        {
            throw new IllegalStateException("Failed to schedule the Job.");
        }
    }

    private void scheduleJob(final JobDetail job, final Trigger trigger)
    {
        try
        {
            scheduler.scheduleJob(job, trigger);
        }
        catch (SchedulerException e)
        {
            throw new IllegalStateException("Failed to schedule the Job.");
        }
    }

    private void rescheduleJob(final JobDetail job, final Trigger trigger)
    {
        try
        {
            scheduler.rescheduleJob(trigger.getName(), this.group, trigger);
        }
        catch (SchedulerException e)
        {
            throw new IllegalStateException("Failed to schedule the Job.");
        }
    }

    private JobDetail createJobDetail(final Object[] args, final String jobName)
    {
        JobDetail detail = new JobDetail(jobName, this.getGroup(), ScheduledJob.class);
        setJobArguments(args, detail);
        setJobToAutoDelete(detail);


        return detail;
    }

    private void setJobToAutoDelete(final JobDetail detail)
    {
        detail.setDurability(false);
    }

    private void setJobArguments(final Object[] args, final JobDetail detail)
    {
        detail.getJobDataMap().put(TARGET_BEAN, targetBean);
        detail.getJobDataMap().put(METHOD_NAME_KEY, targetMethod);
        detail.getJobDataMap().put(ARGUMENTS_KEY, args);
    }

    public static class ScheduledJob implements Job
    {
        @Override
        public void execute(final JobExecutionContext context) throws JobExecutionException
        {
            try
            {
                JobDataMap data = jobData(context);
                invokeMethod(targetBean(data), method(data), arguments(data));
            }
            catch (Exception e)
            {
                throw new JobExecutionException(e);
            }
        }

        private JobDataMap jobData(final JobExecutionContext context)
        {
            return context.getJobDetail().getJobDataMap();
        }

        private Object targetBean(final JobDataMap data) throws Exception
        {
            return data.get(TARGET_BEAN);
        }

        private String method(final JobDataMap data)
        {
            return data.getString(METHOD_NAME_KEY);
        }

        private Object[] arguments(final JobDataMap data)
        {
            return (Object[]) data.get(ARGUMENTS_KEY);
        }

        private void invokeMethod(final Object target, final String method, final Object[] args) throws Exception
        {
            MethodInvoker inv = new ArgumentConvertingMethodInvoker();

            inv.setTargetObject(target);
            inv.setTargetMethod(method);
            inv.setArguments(args);
            inv.prepare();
            inv.invoke();
        }
    }

    public String getGroup()
    {
        return this.group;
    }

    public void setGroup(final String newGroup)
    {
        this.group = newGroup;
    }

    public void setScheduler(final Scheduler newScheduler)
    {
        this.scheduler = newScheduler;
    }

    public void setTargetBean(final Object newTargetBean)
    {
        this.targetBean = newTargetBean;
    }

    public void setTargetMethod(final String newTargetMethod)
    {
        this.targetMethod = newTargetMethod;
    }
}