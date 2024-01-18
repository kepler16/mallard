snapshot := env_var_or_default('SNAPSHOT', 'true')
create_tags := env_var_or_default('CREATE_TAGS', 'false')
include_unchanged := env_var_or_default('INCLUDE_UNCHANGED', 'true')

test:
    clojure -T:kmono run :exec "\"just test\"" \
      ":include-unchanged?" {{ include_unchanged }}
