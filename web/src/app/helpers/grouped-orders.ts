import { OrderDetails, BigInteger } from '../models/protos/models_pb';
import { Big } from 'big.js';

export class Entry {
    constructor(public price: string, public amount: string) { }
}

export class GroupedOrders {

    constructor(public reverseOrder: boolean) { }
    values: Entry[] = [];

    public add(order: OrderDetails.AsObject) {
        const orderIndex = this.values.findIndex(x => x.price.toString() === order.price.value.toString());
        let entry: Entry;

        if (orderIndex >= 0) {
            const entryRemoved = this.values.splice(orderIndex, 1)[0];
            entryRemoved.amount = Big(entryRemoved.amount).plus(Big(order.funds.value));
            entry = new Entry(entryRemoved.price, entryRemoved.amount);
        } else {
            entry = new Entry(order.price.value, order.funds.value);
        }

        this.values.push(entry);
        this.sortOrders();
    }

    public remove(price: BigInteger.AsObject, funds: BigInteger.AsObject): boolean {
        let removed = false;

        // find price
        const groupedIndex = this.values.findIndex(entry => {
            return entry.price === price.value.toString();
        });

        if (groupedIndex >= 0) {
            const entryRemoved = this.values.splice(groupedIndex, 1)[0];
            entryRemoved.amount = Big(entryRemoved.amount).minus(Big(funds.value));

            if (+entryRemoved.amount > 0) {
                this.values.push(new Entry(entryRemoved.price, entryRemoved.amount));
                this.sortOrders();
            }

            removed = true;
        }
        return removed;
    }

    // sell(ask) orders are sorted from lower price to higher price
    // buy(bid) orders are sorted from higher price to lower price
    private sortOrders(): void {

        if (this.reverseOrder) {
            this.values.sort((a, b) => {
                return (+b.price) - (+a.price);
            });
        } else {
            this.values.sort((a, b) => {
                return (+a.price) - (+b.price);
            });
        }
    }
}
