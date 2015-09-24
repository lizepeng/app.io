#!/bin/sh
### BEGIN INIT INFO
# Provides:          app-io
# Required-Start:    $local_fs $remote_fs $network $syslog
# Required-Stop:     $local_fs $remote_fs $network $syslog
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Start/Stop app-io
### END INIT INFO
#
#source some script files in order to set and export environmental variables
#as well as add the appropriate executables to $PATH
calculate_heap_sizes()
{
  case "`uname`" in
    Linux)
      system_memory_in_mb=`free -m | awk '/:/ {print $2;exit}'`
    ;;
    FreeBSD)
      system_memory_in_bytes=`sysctl hw.physmem | awk '{print $2}'`
      system_memory_in_mb=`expr $system_memory_in_bytes / 1024 / 1024`
    ;;
    SunOS)
      system_memory_in_mb=`prtconf | awk '/Memory size:/ {print $3}'`
    ;;
    Darwin)
      system_memory_in_bytes=`sysctl hw.memsize | awk '{print $2}'`
      system_memory_in_mb=`expr $system_memory_in_bytes / 1024 / 1024`
    ;;
    *)
      # assume reasonable defaults for e.g. a modern desktop or
      # cheap server
      system_memory_in_mb="2048"
    ;;
  esac

  # set max heap size based on the following
  memory_in_mb=$(($system_memory_in_mb * 2 / 3))
  if [ "$memory_in_mb" -gt "30500" ]
  then
    memory_in_mb="30500"
  fi

  HEAP_SIZE="${memory_in_mb}M"
}

calculate_heap_sizes

PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
FACT_HOME="/home/admin/current"
START_CMD="${FACT_HOME}/bin/app-io -J-Xms${HEAP_SIZE} -J-Xmx${HEAP_SIZE} -J-server"
PIDFILE="${FACT_HOME}/RUNNING_PID"

USER=admin
GROUP=admin

do_start()
{
  start-stop-daemon --start -p "${PIDFILE}" --quiet --background --chuid ${USER}:${GROUP} --exec /bin/bash -- ${START_CMD}
}

do_stop()
{
  start-stop-daemon -K -p "${PIDFILE}" -u "${USER}" -R 30
}

case "$1" in
  start)
    echo "Starting app-io"
    do_start
    ;;
  stop)
    echo "Stopping app-io"
    do_stop
    ;;
  start-force)
    echo "Forcing to stop app-io"
    do_stop
    rm -rf "${PIDFILE}"
    do_start
    ;;
  stop-force)
    echo "Forcing to stop app-io"
    do_stop
    rm -rf "${PIDFILE}"
    ;;
  *)
    echo "Usage: /etc/init.d/app-io {start|stop}"
    exit 1
    ;;
esac

exit 0