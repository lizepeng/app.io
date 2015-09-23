#!/bin/sh
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

CURRENT_DIR="`dirname \"$0\"`"

cd $CURRENT_DIR/..

./bin/app-io -J-Xms${HEAP_SIZE} -J-Xmx${HEAP_SIZE} -J-server