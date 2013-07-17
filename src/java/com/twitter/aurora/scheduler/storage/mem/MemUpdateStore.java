package com.twitter.aurora.scheduler.storage.mem;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;

import com.twitter.aurora.gen.JobKey;
import com.twitter.aurora.gen.JobUpdateConfiguration;
import com.twitter.aurora.scheduler.base.Tasks;
import com.twitter.aurora.scheduler.storage.UpdateStore;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An in-memory update store.
 */
class MemUpdateStore implements UpdateStore.Mutable {

  private static final Function<JobUpdateConfiguration, JobUpdateConfiguration> DEEP_COPY =
      Util.deepCopier();

  private final Map<String, JobUpdateConfiguration> configs = Maps.newConcurrentMap();

  private String key(String role, String job) {
    checkNotNull(role);
    checkNotNull(job);

    return Tasks.jobKey(role, job);
  }

  private String key(JobKey jobKey) {
    checkNotNull(jobKey);

    return key(jobKey.getRole(), jobKey.getName());
  }

  private String key(JobUpdateConfiguration config) {
    checkNotNull(config);

    if (config.isSetJobKey()) {
      return key(config.getJobKey());
    } else {
      return key(config.getRoleDeprecated(), config.getJobDeprecated());
    }
  }

  @Override
  public void saveJobUpdateConfig(JobUpdateConfiguration config) {
    configs.put(key(config), DEEP_COPY.apply(config));
  }

  @Override
  public void removeShardUpdateConfigs(String role, String job) {
    configs.remove(key(role, job));
  }

  @Override
  public void removeShardUpdateConfigs(JobKey jobKey) {
    configs.remove(key(jobKey));
  }

  @Override
  public void deleteShardUpdateConfigs() {
    configs.clear();
  }

  @Override
  public Optional<JobUpdateConfiguration> fetchJobUpdateConfig(JobKey jobKey) {
    // TODO(ksweeney): Stop ignoring environment here as part of MESOS-2403.
    return Optional.fromNullable(
        configs.get(key(jobKey.getRole(), jobKey.getName()))).transform(DEEP_COPY);
  }

  @Override
  public Set<JobUpdateConfiguration> fetchUpdateConfigs(String role) {
    return FluentIterable.from(configs.values())
        .filter(hasRole(role))
        .transform(DEEP_COPY)
        .toSet();
  }

  @Override
  public Set<String> fetchUpdatingRoles() {
    return FluentIterable.from(configs.values())
        .transform(GET_ROLE)
        .toSet();
  }

  private static final Function<JobUpdateConfiguration, String> GET_ROLE =
      new Function<JobUpdateConfiguration, String>() {
        @Override public String apply(JobUpdateConfiguration config) {
          if (config.isSetJobKey()) {
            return config.getJobKey().getRole();
          } else {
            return config.getRoleDeprecated();
          }
        }
      };

  private static Predicate<JobUpdateConfiguration> hasRole(String role) {
    checkNotNull(role);

    return Predicates.compose(Predicates.equalTo(role), GET_ROLE);
  }
}
