import { GroupedOrders } from 'src/app/helpers/grouped-orders';
import { newOrderDetails } from 'src/app/helpers/protobuf-factory';
import { Big } from 'big.js';


describe('GroupedOrders', () => {
    let buyOrders: GroupedOrders = new GroupedOrders(true);
    let sellOrders: GroupedOrders = new GroupedOrders(true);

    it('should insert orders in the right index', () => {

        buyOrders = new GroupedOrders(true);
        sellOrders = new GroupedOrders(false);

        const buyOrder = newOrderDetails('456789', '57421', '1').toObject();
        const buyOrder2 = newOrderDetails('456789', '57427', '2').toObject();
        const buyOrder3 = newOrderDetails('456789', '57428', '3').toObject();

        const sellOrder = newOrderDetails('321321', '654654', '4').toObject();
        const sellOrder2 = newOrderDetails('321321', '654656', '5').toObject();
        const sellOrder3 = newOrderDetails('321321', '654659', '6').toObject();

        buyOrders.add(buyOrder);
        buyOrders.add(buyOrder2);
        buyOrders.add(buyOrder3);
        sellOrders.add(sellOrder);
        sellOrders.add(sellOrder2);
        sellOrders.add(sellOrder3);

        expect(buyOrders.values.length).toEqual(3);
        expect(sellOrders.values.length).toEqual(3);

        // they must be sorted from lower price to higher price
        expect(buyOrders.values[0].price).toEqual(buyOrder3.price.value);
        expect(buyOrders.values[1].price).toEqual(buyOrder2.price.value);
        expect(buyOrders.values[2].price).toEqual(buyOrder.price.value);

        // they must be sorted from lower price to higher price
        expect(sellOrders.values[0].price).toEqual(sellOrder.price.value);
        expect(sellOrders.values[1].price).toEqual(sellOrder2.price.value);
        expect(sellOrders.values[2].price).toEqual(sellOrder3.price.value);

    });

    it('should collapse orders with the same price instead of creating new items', () => {

        buyOrders = new GroupedOrders(true);
        sellOrders = new GroupedOrders(false);

        const buyOrder = newOrderDetails('456789', '321231', '1').toObject();
        const buyOrder2 = newOrderDetails('456789', '321231', '2').toObject();
        const sellOrder = newOrderDetails('321321', '982135', '3').toObject();
        const sellOrder2 = newOrderDetails('321321', '982135', '4').toObject();

        buyOrders.add(buyOrder);
        buyOrders.add(buyOrder2);
        sellOrders.add(sellOrder);
        sellOrders.add(sellOrder2);

        expect(buyOrders.values.length).toEqual(1);
        expect(sellOrders.values.length).toEqual(1);

    });

    it('should should update an item when removing an order', () => {
        buyOrders = new GroupedOrders(true);
        const testData = [

            newOrderDetails('100000', '321232', '1').toObject(),
            newOrderDetails('50000', '321232', '2').toObject(),
            newOrderDetails('150000', '321232', '3').toObject()

        ];

        testData.forEach(x => buyOrders.add(x));

        expect(buyOrders.values[0].amount).toEqual(Big(300000));
        buyOrders.remove(testData[0].price, testData[0].funds);
        expect(buyOrders.values[0].amount).toEqual(Big(200000));
        buyOrders.remove(testData[1].price, testData[1].funds);
        expect(buyOrders.values[0].amount).toEqual(Big(150000));
        buyOrders.remove(testData[2].price, testData[2].funds);

    });

    it('should remove an item when removing an order left the item with no more orders', () => {
        buyOrders = new GroupedOrders(true);
        const testData = [

            newOrderDetails('100000', '321232', '1').toObject(),
            newOrderDetails('50000', '321232', '2').toObject(),
            newOrderDetails('150000', '321239', '3').toObject()

        ];

        testData.forEach(x => buyOrders.add(x));

        expect(buyOrders.values.length).toEqual(2);
        buyOrders.remove(testData[0].price, testData[0].funds);
        expect(buyOrders.values.length).toEqual(2);
        buyOrders.remove(testData[1].price, testData[1].funds);
        expect(buyOrders.values.length).toEqual(1);
        buyOrders.remove(testData[2].price, testData[2].funds);
        expect(buyOrders.values.length).toEqual(0);

    });
});
