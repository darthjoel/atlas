package com.ning.atlas.components.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.ning.atlas.Host;
import com.ning.atlas.SSH;
import com.ning.atlas.components.ConcurrentComponent;
import com.ning.atlas.config.AtlasConfiguration;
import com.ning.atlas.logging.Logger;
import com.ning.atlas.spi.Component;
import com.ning.atlas.spi.Deployment;
import com.ning.atlas.spi.Identity;
import com.ning.atlas.spi.Maybe;
import com.ning.atlas.spi.Uri;
import com.ning.atlas.spi.protocols.AWS;
import com.ning.atlas.spi.protocols.SSHCredentials;
import com.ning.atlas.spi.protocols.Server;
import com.ning.atlas.spi.space.Missing;
import com.ning.atlas.spi.space.Space;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;

/**
 *
 */
public class EC2Provisioner extends ConcurrentComponent
{
    private final static Logger logger = Logger.get(EC2Provisioner.class);

    private final ConcurrentMap<String, Boolean>   instanceState = Maps.newConcurrentMap();
    private final AtomicReference<AmazonEC2Client> ec2           = new AtomicReference<AmazonEC2Client>();
    private final AtomicReference<String>          keypairId     = new AtomicReference<String>();
    private final String credentialName;

    public EC2Provisioner(Map<String, String> props)
    {
        this.credentialName = props.get("credentials");
    }


    public EC2Provisioner(AWSConfig config)
    {
        BasicAWSCredentials credentials = new BasicAWSCredentials(config.getAccessKey(), config.getSecretKey());
        ec2.set(new AmazonEC2AsyncClient(credentials));
        keypairId.set(config.getKeyPairId());
        this.credentialName = null;
    }

    @Override
    public String perform(final Host node, final Uri<? extends Component> uri, Deployment deployment) throws Exception
    {
        final Space space = deployment.getSpace();
        final Maybe<Server> s = space.get(node.getId(), Server.class, Missing.RequireAll);
        Maybe<EC2InstanceInfo> ec2info = space.get(node.getId(), EC2InstanceInfo.class, Missing.RequireAll);
        if (s.isKnown()
            && ec2info.isKnown()
            && instanceState.containsKey(ec2info.getValue().getEc2InstanceId())
            && instanceState.get(ec2info.getValue().getEc2InstanceId()))
        {
            // we have an ec2 instance for this node already
            logger.info("using existing ec2 instance %s for %s",
                        space.get(node.getId(), EC2InstanceInfo.class, Missing.RequireAll)
                             .getValue()
                             .getEc2InstanceId(),
                        node.getId().toExternalForm());

            return "using existing ec2 instance " + ec2info.getValue().getEc2InstanceId();
        }
        else {
            // spin up an ec2 instance for this node

            final AmazonEC2Client ec2 = EC2Provisioner.this.ec2.get();

            logger.info("Provisioning server for %s", node.getId());
            final String ami_name = uri.getFragment();
            RunInstancesRequest req = new RunInstancesRequest(ami_name, 1, 1);
            if (uri.getParams().containsKey("instance_type")) {
                req.setInstanceType(uri.getParams().get("instance_type"));
            }

            req.setKeyName(keypairId.get());

            final String security_group = Maybe.elideNull(uri.getParams().get("security_group")).otherwise("default");
            AWS.waitForEC2SecurityGroup(security_group, deployment.getSpace(), 1, TimeUnit.MINUTES);
            req.setSecurityGroups(asList(security_group));

            RunInstancesResult rs = ec2.runInstances(req);

            final Instance i = rs.getReservation().getInstances().get(0);

            logger.debug("obtained ec2 instance %s", i.getInstanceId());

            while (true) {

                DescribeInstancesRequest dreq = new DescribeInstancesRequest();
                dreq.setInstanceIds(Lists.newArrayList(i.getInstanceId()));
                DescribeInstancesResult res = null;
                try {
                    res = ec2.describeInstances(dreq);
                }
                catch (AmazonServiceException e) {
                    // sometimes amazon says the instance doesn't exist yet,
                    if (!e.getMessage().contains("does not exist")) {
                        throw new UnsupportedOperationException("Not Yet Implemented!", e);
                    }
                }
                if (res != null) {
                    Instance i2 = res.getReservations().get(0).getInstances().get(0);
                    if ("running".equals(i2.getState().getName()) && canSshIn(i2, deployment)) {
                        logger.info("Obtained instance %s at %s for %s",
                                    i2.getInstanceId(), i2.getPublicDnsName(), node.getId());
                        Server server = new Server();
                        server.setExternalAddress(i2.getPublicDnsName());
                        server.setInternalAddress(i2.getPrivateDnsName());

                        EC2InstanceInfo info = new EC2InstanceInfo();
                        info.setEc2InstanceId(i2.getInstanceId());
                        space.store(node.getId(), info);
                        space.store(node.getId(), server);


                        String name = node.getId().toExternalForm().length() > 255
                                      ? node.getId().toExternalForm().substring(0, 255)
                                      : node.getId().toExternalForm();
                        ec2.createTags(new CreateTagsRequest(asList(i2.getInstanceId()),
                                                             asList(new Tag("Name", name))));

                        return "created new ec2 instance " + info.getEc2InstanceId();
                    }
                    else {
                        try {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new UnsupportedOperationException("Not Yet Implemented!", e);
                        }
                    }
                }
            }
        }
    }

    private boolean canSshIn(Instance i2, Deployment deployment)
    {
        try {
            SSH ssh = new SSH(SSHCredentials.lookup(deployment.getSpace(), credentialName)
                                            .otherwise(SSHCredentials.defaultCredentials(deployment.getSpace()))
                                            .otherwise(new IllegalStateException("No SSH credentials available")),
                              i2.getPublicDnsName());
            ssh.exec("ls -l");
            ssh.close();
            return true;
        }
        catch (IOException e) {
            // cannot get there yet!
        }
        return false;
    }

    @Override
    public String unwind(Identity hostId, Uri<? extends Component> uri, Deployment d) throws Exception
    {
        EC2InstanceInfo ec2info = d.getSpace().get(hostId, EC2InstanceInfo.class, Missing.RequireAll)
                                   .otherwise(new IllegalStateException("Nop instance id found"));
        final AmazonEC2Client ec2 = EC2Provisioner.this.ec2.get();

        TerminateInstancesRequest req = new TerminateInstancesRequest(asList(ec2info.getEc2InstanceId()));

        ec2.terminateInstances(req);

        return "terminated ec2 instance";
    }

    @Override
    protected void startLocal(Deployment deployment)
    {
        AtlasConfiguration config = AtlasConfiguration.global();
        BasicAWSCredentials credentials = new BasicAWSCredentials(config.lookup("aws.key").get(),
                                                                  config.lookup("aws.secret").get());

        Space s = deployment.getSpace();
        AWS.SSHKeyPairInfo info = s.get(AWS.ID, AWS.SSHKeyPairInfo.class, Missing.RequireAll)
                                   .otherwise(new IllegalStateException("unable to find aws ssh keypair info"));

        this.keypairId.set(info.getKeyPairId());
        this.ec2.set(new AmazonEC2AsyncClient(credentials));

        DescribeInstancesResult rs = this.ec2.get().describeInstances();
        for (Reservation reservation : rs.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                instanceState.put(instance.getInstanceId(), instance.getState().getName().equals("running"));
            }
        }

    }


    @Override
    public Future<String> describe(Host server,
                                   Uri<? extends Component> uri,
                                   Deployment deployment)
    {
        return Futures.immediateFuture("provision ec2 instance");
    }

    public void destroy(Identity id, Space space)
    {
        EC2InstanceInfo info = space.get(id, EC2InstanceInfo.class, Missing.RequireAll).getValue();
        ec2.get().terminateInstances(new TerminateInstancesRequest(asList(info.getEc2InstanceId())));
        logger.info("destroyed ec2 instance %s", info.getEc2InstanceId());
    }

    public static class EC2InstanceInfo
    {
        private String ec2InstanceId;

        public String getEc2InstanceId()
        {
            return ec2InstanceId;
        }

        public void setEc2InstanceId(String ec2InstanceId)
        {
            this.ec2InstanceId = ec2InstanceId;
        }
    }
}
